@file:JvmName("ParameterRemapperApp")

package xyz.acrylicstyle.parameterRemapper

import util.MultiCollection
import util.function.BuiltinStringConverter
import util.option.OptionParser
import xyz.acrylicstyle.parameterRemapper.util.Mapping
import xyz.acrylicstyle.parameterRemapper.util.RemapInfo
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val parser = OptionParser()
    parser.accepts("help", "this")
    parser.accepts("debug", "enable debug logging")
    parser.accepts("verbose", "enable verbose logging")
    val argMappingFile = parser.accepts("mapping-file", "the mapping file to load").withRequiredArg().ofType(File::class.java).defaultsTo(File("./mappings.txt"))
    val argInputFile = parser.accepts("input-file", "the input jar file path to process").withRequiredArg().ofType(File::class.java).defaultsTo(File("./input.jar"))
    val argOutputFile = parser.accepts("output-file", "the output jar file path").withRequiredArg().ofType(File::class.java).defaultsTo(File("./remapped.jar"))
    val argThreads = parser.accepts("threads", "thread count to process classes").withRequiredArg().ofType(BuiltinStringConverter.INTEGER).defaultsTo(8)
    val result = parser.parse(*args)
    if (result.has("help")) {
        parser.printHelpOn(System.out)
        exitProcess(0)
    }
    val debug = result.has("debug")
    val verbose = result.has("verbose")
    val mappingFile = result.value(argMappingFile)!!
    val inputFile = result.value(argInputFile)!!
    val outputFile = result.value(argOutputFile)!!
    val threads = result.value(argThreads)!!
    if (!mappingFile.exists() || mappingFile.isDirectory) {
        println("mapping file " + mappingFile.absolutePath + " does not exist")
        exitProcess(1)
    }
    if (!inputFile.exists() || inputFile.isDirectory) {
        println("input file " + inputFile.absolutePath + " does not exist")
        exitProcess(1)
    }
    if (threads <= 0) {
        println("Invalid thread count: $threads")
        exitProcess(1)
    }
    val mapping = Mapping()
    val ln = AtomicInteger()
    println("Loading mapping file")
    mappingFile.forEachLine { line ->
        ln.incrementAndGet()
        if (line.isEmpty()) return@forEachLine
        try {
            // class method signature <remap info, see below>
            val arr = line.split("\\s+".toRegex())
            if (arr.isEmpty()) return@forEachLine
            val clazz = arr[0]
            val method = arr[1]
            val signature = arr[2]
            if (signature.endsWith(")")) throw IllegalArgumentException("No return type for signature: $signature")
            for (i in 3 until arr.size) {
                if (!mapping.containsKey(clazz)) mapping.add(clazz, MultiCollection())
                // accepts these "remap info":
                // oldName->newName (find by old name)
                // ->newName (find by param index, starting from 0)
                // -> (skip param index)
                if (arr[i] == "->") {
                    mapping[clazz]!!.add(method + signature, RemapInfo("", "", i - 3, null, "", true))
                    continue
                }
                val arr1 = arr[i].split("->")
                val oldName = if (arr1.size == 1 || arr1[0].isEmpty()) null else arr1[0]
                val newName = if (arr1.size == 1) arr1[0] else arr1[1]
                mapping[clazz]!!.add(method + signature, RemapInfo(method, signature, i - 3, oldName, newName, oldName != null))
            }
        } catch (e: Throwable) {
            System.err.println("Invalid mapping at line ${ln.get()}: $line")
            System.err.println("Error message: ${e.javaClass.simpleName}: ${e.message}")
            if (debug) e.printStackTrace()
        }
    }
    ParameterRemapper(mapping, inputFile, outputFile, threads, verbose, debug).run()
    println("Complete")
}
