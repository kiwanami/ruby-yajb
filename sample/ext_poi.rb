#!/usr/bin/ruby

# 
# sample script: 
#       POI Demo (Using other class library)
# this script file is encoded by UTF-8.

require 'yajb'
include JavaBridge

JBRIDGE_OPTIONS = {
  :classpath => "$CLASSPATH;poi.jar", # for windows (classpath separator ";")
}

jimport "java.io.*"
jimport "org.apache.poi.hssf.usermodel.*"


workbook = jnew :HSSFWorkbook

sheet = workbook.createSheet("new sheet")
row = sheet.createRow(0)
row.createCell(0).setCellValue(1)
row.createCell(1).setCellValue(1.2)
row.createCell(2).setCellValue("This is a string")

cell = row.createCell(3)
cell.setEncoding(:HSSFCell.jclass.ENCODING_UTF_16)
cell.setCellValue("POIですよ")

out = jnew :FileOutputStream, "a.xls"
workbook.write(out)
out.close
