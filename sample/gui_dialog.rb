#!/usr/bin/ruby

# 
# GUI sample script: 
#       JDialog Demo (Handling the Java-AWT thread)
# 
# This script demonstrates the Java-like new method.
# 


require 'yajb'
include JavaBridge

jimport "java.awt.*"
jimport "java.awt.event.*"
jimport "javax.swing.*"

label = jnew :JLabel, "Hello World!"

button = jnew :JButton, "open!"
open_action = jextend :ActionListener
button.addActionListener(open_action)

panel = jnew :JPanel, jnew(:GridLayout,2,1)
panel.add(label)
panel.add(button)

frame = jnew :JFrame, "JDialog DEMO"
frame.getContentPane.add(panel)
frame.pack

open_action.jdef(:actionPerformed) do |event|

  dlg = jnew :JDialog, frame, "Modal Dialog!", true
  text = jnew :JTextField,12
  okbtn = jnew :JButton,"OK"

  okbtn.addActionListener(jextend(:ActionListener) { |name, args|
							label.setText(text.getText())
							dlg.dispose()
						  }) ## java-like anonymous class implementation
  
  panel = jnew :JPanel
  panel.add(text)
  panel.add(okbtn)
  
  dlg.getContentPane.add(panel)
  dlg.pack
  dlg.show
end

wc = jextend :WindowAdapter
wc.jdef(:windowClosing) do |e|
  wakeup_thread
end
frame.addWindowListener(wc)
frame.show()

stop_thread
