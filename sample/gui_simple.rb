#!/usr/bin/ruby

# 
# GUI sample script: 
#       Simple GUI (Implementation of java.awt.event interfaces)
# 

require 'yajb'
include JavaBridge

jimport "javax.swing.*"
jimport "java.awt.event.*"
jimport "java.awt.GridLayout"

text = jnew :JTextField, "Hello World!"
button1 = jnew :JButton, "send message to ruby"
action1 = jextend :ActionListener
action1.jdef(:actionPerformed) do |event| ## implementation by block
  puts "called by Java : #{text.getText}"
end
button1.addActionListener(action1)

button2 = jnew :JButton, "exit"
action2 = jextend :ActionListener
def action2.actionPerformed(event) ## implementation by singleton method
  wakeup_thread
end
button2.addActionListener(action2)

panel = jnew(:JPanel,jnew(:GridLayout,3,1))
panel.add(text)
panel.add(button1)
panel.add(button2)

frame = jnew :JFrame 
frame.getContentPane.add(panel)
frame.pack
frame.show

stop_thread
