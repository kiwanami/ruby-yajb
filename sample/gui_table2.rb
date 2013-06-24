#!/usr/bin/ruby

# 
# GUI sample script: 
#       JTable Demo (Implementation of MVC)
# 

require 'yajb'
include JavaBridge

jimport "java.awt.*"
jimport "java.awt.event.*"
jimport "javax.swing.*"
jimport "javax.swing.table.AbstractTableModel"
jimport "javax.swing.table.DefaultTableCellRenderer"

$light_blue = :Color.jnew(0.9,1.0,0.95)
$white = :Color.jclass.white

def make_model  ## implementation of Model
  model = :AbstractTableModel.jext
  class << model

    def init
      @data = [ 
        ["Input","comma","separated","text"],
        ["1","2","3","4"],
      ]
    end

    def add_row(row)
      @data << row
      fireTableStructureChanged
    end

    def getColumnCount
      col = 0
      @data.each{|i|
        col = i.size if i.size > col
      }
      return col
    end
    
    def getRowCount
      @data.size
    end
    
    def getValueAt(row, col)
      ret = @data[row][col]
      return ret if ret
      return ""
    end

  end # class model

  model.init
  return model
end

def make_cell_renderer
  renderer = :DefaultTableCellRenderer.jext
  renderer.setOpaque(true)
  class << renderer
    def getTableCellRendererComponent(table,value,is_selected,has_focus,row,col)
      comp = super(table,value,is_selected,has_focus,row,col) ### superclass method!
      if (is_selected == false && has_focus == false) then
        if ((row % 2) == 1) 
          comp.setBackground($light_blue)
        else 
          comp.setBackground($white)
        end
      end
      return comp
    end
  end
  return renderer
end

def make_main_panel(model)
  text_field = :JTextField.jnew(30)

  add_event = :ActionListener.jext 
  add_event.jdef(:actionPerformed) do |ev|
	model.add_row( text_field.getText.split(/,/) )
  end

  add_button = :JButton.jnew("Add")
  add_button.addActionListener(add_event)

  text_line = :JPanel.jnew
  text_line.add(text_field)
  text_line.add(add_button)

  table = :JTable.jnew(model)
  table.setDefaultRenderer(:Object.jclass, make_cell_renderer)
  scroll = :JScrollPane.jnew(table)

  panel = :JPanel.jnew( :BorderLayout.jnew )
  panel.add( scroll, :BorderLayout.jclass.CENTER )
  panel.add( text_line, :BorderLayout.jclass.SOUTH )

  return panel
end

def show_window(model)
  frame = :JFrame.jnew
  frame.getContentPane.add( make_main_panel(model) )
  frame.setSize(600,400)

  wc = :WindowAdapter.jext
  wc.jdef :windowClosing do |e|
	wakeup_thread
  end
  frame.addWindowListener(wc)
  frame.show

  stop_thread
end

show_window( make_model )

