#!/usr/bin/ruby

# 
# GUI sample script: 
#       JTable Demo (Implementation of Model)
# 
# This script demonstrates the Ruby-like new method.
# 

require 'yajb'
include JavaBridge

jimport "java.awt.event.*"
jimport "javax.swing.*"
jimport "javax.swing.table.AbstractTableModel"

$data = [ ["Mon", "Tue", "Wed" ,"Thu", "Fri", "Sat", "Sun"],
  ["01", "02", "03" ,"04", "05", "06", "07"],
  ["08", "09", "10" ,"11", "12", "13", "15"],
  ["16", "17", "18" ,"19", "20", "21", "22"] ]

model = :AbstractTableModel.jext

class << model
  def getColumnCount
	$data.first.size
  end

  def getRowCount
	$data.size
  end

  def getValueAt(row, col)
	$data[row][col]
  end
end

table = :JTable.jnew(model)
scroll = :JScrollPane.jnew(table)

frame = :JFrame.jnew
frame.getContentPane.add(scroll)
frame.setSize(600,400)

wc = :WindowAdapter.jext
wc.jdef(:windowClosing) {|e|
  wakeup_thread
}
frame.addWindowListener(wc)
frame.show()

stop_thread
