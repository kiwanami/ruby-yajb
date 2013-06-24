#!/usr/bin/ruby

# 
# GUI sample script: 
#       Drawing Graphics (Ruby can draw picture!)
# 

require 'yajb'
include JavaBridge

jimport "java.awt.*"
jimport "java.awt.event.*"
jimport "javax.swing.*"

$jcolor = :Color.jclass
$jfont = :Font.jclass

bgcolor = $jcolor.white
midcolor = $jcolor.lightGray
lettercolor = $jcolor.red
font = :Font.jnew("Serif", $jfont.BOLD, 30)

comp = jextend :JComponent
comp.jdef :paintComponent do |g|
  d = comp.getSize
  wd = d.width
  ht = d.height
  g.setColor bgcolor
  g.fillRect(0, 0, wd, ht)
  
  g.setColor midcolor
  g.fillRect(50, 50, wd-100, ht-100)
  
  g.setColor lettercolor
  g.setFont font
  m = "Yet Another Java Bridge"
  sz = g.getFontMetrics.stringWidth(m)
  g.drawString(m,(wd-sz)/2,200)
end

frame = jnew :JFrame 
frame.getContentPane.add(comp)
frame.setSize(500,500)

wc = jextend :WindowAdapter
wc.jdef(:windowClosing) do |e|
  wakeup_thread
end
frame.addWindowListener(wc)

frame.show

stop_thread
