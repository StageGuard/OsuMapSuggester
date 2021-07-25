package me.stageguard.obms.utils

import org.apache.commons.io.input.BOMInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

fun File.bomReader() = BufferedReader(InputStreamReader(BOMInputStream(FileInputStream(this))))