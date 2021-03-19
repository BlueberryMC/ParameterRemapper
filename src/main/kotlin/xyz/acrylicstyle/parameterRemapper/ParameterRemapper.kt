package xyz.acrylicstyle.parameterRemapper

import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import xyz.acrylicstyle.parameterRemapper.util.Mapping
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ParameterRemapper(
    private val mapping: Mapping,
    private val inputFile: File,
    private val outputFile: File,
    private val threads: Int,
    private val verbose: Boolean,
    private val debug: Boolean,
) {
    private val unseen = ArrayList<String>()
    private val remapped = ArrayList<String>()

    fun run() {
        val progress = ProgressBarBuilder()
            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK)
            .setInitialMax(0)
            .showSpeed()
            .build()

        outputFile.delete()

        println("Start mapping")
        val mappingExecutor = Executors.newFixedThreadPool(threads)
        val outputExecutor = Executors.newSingleThreadExecutor()

        val input = ZipFile(inputFile)
        val output = ZipOutputStream(FileOutputStream(outputFile))

        for (entry in input.entries()) {
            progress.maxHint(progress.max + 1)
            val bytes = input.getInputStream(entry).readBytes()
            if (entry.name.endsWith(".class")) {
                mappingExecutor.execute {
                    val cr = ClassReader(bytes)
                    val node = applyMapping(cr)
                    val cw = ClassWriter(0)
                    node.accept(cw)
                    outputExecutor.execute {
                        output.putNextEntry(ZipEntry(entry.name))
                        ByteArrayInputStream(cw.toByteArray()).copyTo(output)
                        output.closeEntry()
                        progress.step()
                    }
                }
            } else {
                outputExecutor.execute {
                    output.putNextEntry(ZipEntry(entry.name))
                    ByteArrayInputStream(bytes).copyTo(output)
                    output.closeEntry()
                    progress.step()
                }
            }
        }

        mappingExecutor.shutdown()
        mappingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS)
        outputExecutor.shutdown()
        outputExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.HOURS)
        output.close()
        progress.close()
        if (verbose && remapped.isNotEmpty()) {
            println("Remapped local variables:")
            remapped.forEach { println(it) }
        }
        if (unseen.isNotEmpty()) {
            println("Unseen param/methods:")
            unseen.forEach { println(it) }
        }
    }

    private fun applyMapping(cr: ClassReader): ClassNode {
        val node = ClassNode()
        val seenMethods = HashSet<String>()
        val visitor = object : ClassVisitor(Opcodes.ASM8, node) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String?,
                methodSignature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                val methodVisitor = super.visitMethod(access, name, descriptor, methodSignature, exceptions)
                if (!mapping.containsKey(cr.className)) {
                    return methodVisitor
                }
                if (debug) println("Seen method: ${cr.className} $name $descriptor")
                if (!mapping[cr.className]!!.containsKey(name + descriptor)) {
                    return methodVisitor
                }
                val paramMapping = mapping[cr.className]!![name + descriptor]!!
                val seen = HashSet<Int>()
                seenMethods.add("${cr.className},$name $descriptor")
                val seenThis = AtomicBoolean(false)
                return object : MethodVisitor(Opcodes.ASM8, methodVisitor) {
                    override fun visitLocalVariable(
                        lvName: String?,
                        lvDescriptor: String?,
                        lvSignature: String?,
                        start: Label?,
                        end: Label?,
                        _index: Int
                    ) {
                        if (_index == 0 && lvName.equals("this")) {
                            seenThis.set(true)
                            return super.visitLocalVariable(lvName, lvDescriptor, lvSignature, start, end, _index)
                        }
                        val index = if (seenThis.get()) _index - 1 else _index
                        if (debug) println("Seen local variable: ${cr.className} $name $descriptor: $lvName; param index $index (raw: $_index)")
                        val info = paramMapping.find { info -> info.oldName != null && info.oldName == lvName }
                        var newName = lvName
                        if (info != null) {
                            newName = info.newName
                            seen.add(index)
                            if (verbose) remapped.add("Remapped $lvName -> $newName in ${cr.className} $name $descriptor")
                        } else {
                            if (paramMapping.has(index)) {
                                seen.add(index)
                                if (!paramMapping[index].dummy) {
                                    newName = paramMapping[index].newName
                                    if (verbose) remapped.add("Remapped $lvName -> $newName in ${cr.className} $name $descriptor")
                                }
                            }
                        }
                        super.visitLocalVariable(newName ?: lvName, lvDescriptor, lvSignature, start, end, _index)
                    }

                    override fun visitEnd() {
                        paramMapping.foreach { info, index ->
                            if (!seen.contains(index)) {
                                unseen.add((if (info.dummy) "Possibly " else "") + "Unseen param: ${cr.className} $name $descriptor : param index $index")
                            }
                        }
                        super.visitEnd()
                    }
                }
            }

            override fun visitEnd() {
                if (mapping.containsKey(cr.className)) {
                    mapping[cr.className]!!.foreach { _, info ->
                        if (info.dummy || seenMethods.contains("${cr.className},${info.method} ${info.signature}")) return@foreach
                        unseen.add("Unseen method: ${cr.className} ${info.method} ${info.signature}")
                    }
                }
                super.visitEnd()
            }
        }
        cr.accept(visitor, 0)
        return node
    }
}
