#Author::    Masashi Sakurai (mailto:m.sakurai@dream.com)
#Copyright:: Copyright (C) 2005-2006 Masashi Sakurai 
#License::   Distributes under LGPL
#
# inou-rpc for ruby
#

require 'socket'
require 'monitor'
require 'thread'
require 'stringio'

#The binary stream encoding protocol.
module INOURPC

  M_NONE     =   -1
  M_CALL     = 0x00
  M_RETURN   = 0x01
  
  T_NULL     = 0x10
  T_INTEGER1 = 0x11
  T_INTEGER2 = 0x12
  T_INTEGER4 = 0x13
  T_INTEGER8 = 0x14
  T_FLOAT    = 0x15
  T_DOUBLE   = 0x16
  T_DECIMAL  = 0x17
  T_STRING   = 0x18
  T_ARRAY    = 0x19
  T_LIST     = 0x1a
  T_HASH     = 0x1d
  
  T_BOOLEAN_TRUE  = 0x1b
  T_BOOLEAN_FALSE = 0x1c
  
  R_OK              = 0x20
  R_PROTOCOL_ERROR  = 0x21
  R_APP_ERROR       = 0x22
  R_FATAL_ERROR     = 0x23

  @@table_tconst2str = {
   T_NULL => "T_NULL",
   T_DECIMAL => "T_DECIMAL",
   T_INTEGER8 => "T_INTEGER8",
   T_INTEGER4 => "T_INTEGER4",
   T_INTEGER2 => "T_INTEGER2",
   T_INTEGER1 => "T_INTEGER1",
   T_FLOAT => "T_FLOAT",
   T_DOUBLE => "T_DOUBLE",
   T_DECIMAL => "T_DECIMAL",
   T_STRING => "T_STRING",
   T_BOOLEAN_TRUE => "T_BOOLEAN_TRUE",
   T_BOOLEAN_FALSE => "T_BOOLEAN_FALSE",
   T_ARRAY => "T_ARRAY",
   T_LIST => "T_LIST",
   T_HASH => "T_HASH",
  }
  @@table_tconst2str.default = "(unknown)"

  #############################################################
  #  Encoder
  #############################################################

  module Encoder

	def writes(a)
	  raise "Not implemented "
	end

	def check_type(arg)
	  return T_NULL if arg.nil?
	  c = arg.class
	  if c ==  Bignum then
		targ = arg.abs
		if (0x7fff_ffff_ffff_ffff & targ) == targ then
		  return T_INTEGER8
		else
		  return T_DECIMAL
		end
	  elsif c ==  Fixnum then
		targ = arg.abs
		if (0x7f & targ) == targ then
		  return T_INTEGER1
		elsif (0x7fff & targ) == targ then
		  return T_INTEGER2
		elsif (0x7fff_ffff & targ) == targ then
		  return T_INTEGER4
		end
	  elsif c ==  Float then
		return T_DOUBLE
	  elsif c ==  String then
		return T_STRING
	  elsif c ==  Array then
		return T_ARRAY
	  elsif c ==  Hash then
		return T_HASH
	  elsif c ==  TrueClass then
		return T_BOOLEAN_TRUE
	  elsif c ==  FalseClass then
		return T_BOOLEAN_FALSE
	  end
	  return nil
	end

	# general object writer
	def write(arg)
	  case check_type(arg)
	  when T_NULL
		return write_null
	  when T_BOOLEAN_TRUE
		return writes([T_BOOLEAN_TRUE].pack("C"))
	  when T_BOOLEAN_FALSE
		return writes([T_BOOLEAN_FALSE].pack("C"))
	  when T_INTEGER1
		return write_int1(arg)
	  when T_INTEGER2
		return write_int2(arg)
	  when T_INTEGER4
		return write_int4(arg)
	  when T_DOUBLE
		return write_double(arg)
	  when T_STRING
		return write_string(arg)
	  when T_INTEGER8
		return write_int8(arg)
	  when T_DECIMAL
		return write_decimal(arg)
	  when T_ARRAY
		return write_array(arg)
	  when T_HASH
		return write_hash(arg)
	  end
	  raise IOError.new("Can not serialize the type: #{arg.class} : #{arg}")
	end

	# primitive writer
	def write_null(arg=nil)
	  return writes( [T_NULL].pack("C") )
	end

	# primitive writer
	def write_bool(arg)
	  return writes( [ arg ? T_BOOLEAN_TRUE : T_BOOLEAN_FALSE ].pack("C") )
	end
	
	# primitive writer
	def write_int1(arg)
	  return writes( [T_INTEGER1,arg].pack("CC") )
	end

	# primitive writer
	def write_int2(arg)
	  return writes( [T_INTEGER2,arg].pack("Cv") )
	end

	# primitive writer
	def write_int4(arg)
	  return writes( [T_INTEGER4,arg].pack("CV") )
	end

	# primitive writer
	def write_int8(arg)
	  return writes( [T_INTEGER8,(arg & 0xffff_ffff),( (arg >> 32) & 0xffff_ffff)].pack("CVV") )
	end

	# primitive writer
	def write_decimal(arg)
	  writes( [T_DECIMAL].pack("C") )
	  return 1 + write_string_gen(arg.to_s)
	end

	# primitive writer
	def write_string(arg)
	  writes( [T_STRING].pack("C") )
	  return 1 + write_string_gen(arg)
	end

	# primitive writer
	def write_string_gen(arg)
	  if arg 
		return writes( [arg.size, arg].pack("Va*") )
	  else
		return writes( [-1].pack("V") )
	  end
	end

	# primitive writer
	def write_double(arg)
	  return writes( [T_DOUBLE, arg].pack("CE") )
	end

	# primitive writer
	def write_float(arg)
	  return writes( [T_FLOAT, arg].pack("Ce") )
	end

	### array writer

	# [type_symbol, values... ] -> type, [values...]
	# [values... ] -> any_type, [values...]
	# type_symbol => :t_int1, :t_int2, :t_int4, :t_int8, :t_float, :t_double
	#                :t_decimal, :t_string, :t_boolean
	def check_type_array(array)
	  if array.first.class == Symbol then
		ctype = array.shift
		case ctype
		when :t_int1,:t_byte
		  return T_INTEGER1,array
		when :t_int2,:t_short
		  return T_INTEGER2,array
		when :t_int4,:t_int
		  return T_INTEGER4,array
		when :t_int8,:t_long
		  return T_INTEGER8,array
		when :t_float
		  return T_FLOAT,array
		when :t_double
		  return T_DOUBLE,array
		when :t_decimal
		  return T_DECIMAL,array
		when :t_string
		  return T_STRING,array
		when :t_boolean
		  return T_BOOLEAN_TRUE,array
		end
	  end
	  return nil,array # any_type array
	end

	# general array writer
	def write_array(arg)
	  return write_null if arg.nil? 
	  return write_zero_array(arg) if arg.size == 0

	  head = (Symbol === arg[0]) ? arg[0] : nil

	  begin
		ctype,array = check_type_array(arg)

		case ctype
		when T_NULL
		  return write_zero_array(array)
		when T_DECIMAL
		  return write_decimal_array(array)
		when T_INTEGER8
		  return write_int8_array(array)
		when T_INTEGER4
		  return write_int4_array(array)
		when T_INTEGER2
		  return write_int2_array(array)
		when T_INTEGER1
		  return write_int1_array(array)
		when T_FLOAT
		  return write_float_array(array)
		when T_DOUBLE
		  return write_double_array(array)
		when T_STRING
		  return write_string_array(array)
		when T_BOOLEAN_TRUE
		  return write_bool_array(array)
		else
		  write_list(arg)
		end
	  ensure
		arg.unshift(head) if head
	  end
	end

	def write_zero_array(arg)
	  return writes( [T_ARRAY, T_NULL, 0].pack("CCV"))
	end

	def write_array_gen(arg,ctype,pack_str)
	  if arg.size == 0
		return writes( [T_ARRAY, ctype, 0].pack("CCV") )
	  else
		return writes( [T_ARRAY, ctype, arg.size].concat(arg).pack("CCV#{pack_str}") )
	  end
	end

	def write_packed_array_gen(arg,ctype)
	  if arg.size == 0
		return writes( [T_ARRAY, ctype, 0].pack("CCV") )
	  else
		return writes( [T_ARRAY, ctype, arg[0].size].pack("CCV").concat(arg[0]) )
	  end
	end

	def write_bool_array(arg)
	  aa = arg.collect {|i| i ? T_BOOLEAN_TRUE : T_BOOLEAN_FALSE }
	  return write_array_gen(aa,T_BOOLEAN_TRUE,"C*")
	end

	def write_int1_array(arg)
	  if arg.size > 0 && arg[0].kind_of?(String)
		return write_packed_array_gen(arg,T_INTEGER1)
	  else
		return write_array_gen(arg,T_INTEGER1,"C*")
	  end
	end

	def write_int2_array(arg)
	  return write_array_gen(arg,T_INTEGER2,"v*")
	end

	def write_int4_array(arg)
	  return write_array_gen(arg,T_INTEGER4,"V*")
	end

	def write_int8_array(arg)
	  if arg.size == 0
		return writes( [T_ARRAY, T_INTEGER8, 0].pack("CCV") )
	  else
		aa = arg.inject([]) {|result,i| 
		  result << (i & 0xffff_ffff)
		  result << ((i >> 32) & 0xffff_ffff)
		}
		return writes( [T_ARRAY, T_INTEGER8, arg.size].concat(aa).pack("CCVV*") )
	  end
	end

	def write_array_str_gen(arg, ctype)
	  sz = writes [T_ARRAY, ctype, arg.size].pack("CCV")
	  arg.each {|i|
		sz += write_string_gen(i)
	  }
	  return sz
	end

	def write_double_array(arg)
	  return write_array_gen(arg,T_DOUBLE,"E*")
	end

	def write_float_array(arg)
	  return write_array_gen(arg,T_FLOAT,"e*")
	end

	def write_decimal_array(arg)
	  return write_array_str_gen(arg.collect {|i| i.to_s }, T_DECIMAL)
	end

	def write_string_array(arg)
	  return write_array_str_gen(arg,T_STRING)
	end

	### list writer

	def write_list(arg)
	  return write_null if arg.nil? 
	  sz = writes [T_LIST, arg.size].pack("CV")
	  arg.each{|i|
		sz += write(i)
	  }
	  return sz
	end

	def write_hash(arg)
	  return write_null if arg.nil? 
	  sz = writes [T_HASH, arg.size].pack("CV")
	  arg.each{|key,val|
		sz += write(key)
		sz += write(val)
	  }
	  return sz
	end

	private :writes, :check_type, :check_type_array, :write_string_gen, :write_array_gen, :write_array_str_gen

  end #module Encoder


  #############################################################
  #  Decoder
  #############################################################

  module Decoder

	private 

	def us2s(a,size)
	  return a if a[size*8-1] == 0
	  return a-(1<<(8*size))
	end

	def us2s_array(aa,size)
	  topbit = size*8-1
	  maxnum = 1<<(8*size)
	  aa.collect!{|i| 
		if i[topbit] == 0 then
		  i
		else
		  i-maxnum
		end
	  }
	  return aa
	end

	def read_int2(input)
	  return us2s(input.read(2).unpack("v").shift,2)
	end

	def read_int4(input)
	  return us2s(input.read(4).unpack("V").shift,4)
	end

	def read_int8(input)
	  lval = input.read(4).unpack("V").shift
	  hval = input.read(4).unpack("V").shift
	  return us2s( ((hval<<32) | lval) ,8)
	end

	public

	#* type, value = read(input)
	#* input: IO object.
	#* return: content type and value object
	def read(input)
	  header = input.getc
	  raise EOFError.new() if header.nil?
	  case header
	  when T_NULL
		return T_NULL,nil
	  when T_BOOLEAN_TRUE
		return T_BOOLEAN_TRUE,true
	  when T_BOOLEAN_FALSE
		return T_BOOLEAN_FALSE,false
	  when T_INTEGER1
		return T_INTEGER1,input.read(1).unpack("c").shift
	  when T_INTEGER2
		return T_INTEGER2,read_int2(input)
	  when T_INTEGER4
		return T_INTEGER4,read_int4(input)
	  when T_INTEGER8
		return T_INTEGER8,read_int8(input)
	  when T_FLOAT
		val = input.read(4).unpack("e").shift
		return T_FLOAT,val
	  when T_DOUBLE
		val = input.read(8).unpack("E").shift
		return T_DOUBLE,val
	  when T_STRING
		len = read_int4(input)
		str = input.read(len)
		return T_STRING,str
	  when T_DECIMAL
		len = read_int4(input)
		str = input.read(len)
		val = str["."] ? str.to_f : str.to_i
		return T_DECIMAL, val
	  when T_ARRAY
		len,val = read_array(input)
		return T_ARRAY,val
	  when T_LIST
		len,val = read_list(input)
		return T_LIST,val
	  when T_HASH
		len,val = read_hash(input)
		return T_HASH,val
	  else
		raise IOError.new("Decoder: wrong header: #{header.to_s}")
	  end
	end

	#* read_block(input) { |type, value| ... }
	#* input: IO object.
	#* yield: content type and value object
	#* return: read buffer length
	def read_block(input)
	  header = input.getc
	  raise EOFError.new() if header.nil?
	  case header
	  when T_NULL
		yield T_NULL,nil
		return 1
	  when T_BOOLEAN_TRUE
		yield T_BOOLEAN_TRUE,true
		return 1
	  when T_BOOLEAN_FALSE
		yield T_BOOLEAN_FALSE,false
		return 1
	  when T_INTEGER1
		val = input.read(1).unpack("c").shift
		yield T_INTEGER1,val
		return 2
	  when T_INTEGER2
		yield T_INTEGER2,read_int2(input)
		return 3
	  when T_INTEGER4
		yield T_INTEGER4,read_int4(input)
		return 5
	  when T_INTEGER8
		yield T_INTEGER8,read_int8(input)
		return 9
	  when T_FLOAT
		val = input.read(4).unpack("e").shift
		yield T_FLOAT,val
		return 5
	  when T_DOUBLE
		val = input.read(8).unpack("E").shift
		yield T_DOUBLE,val
		return 9
	  when T_STRING
		len = read_int4(input)
		str = input.read(len)
		yield T_STRING,str
		return 5+len
	  when T_DECIMAL
		len = read_int4(input)
		str = input.read(len)
		val = str["."] ? str.to_f : str.to_i
		yield T_DECIMAL, val
		return 5+len
	  when T_ARRAY
		len,val = read_array(input)
		yield T_ARRAY,val
		return 1+len
	  when T_LIST
		len,val = read_list(input)
		yield T_LIST,val
		return 1+len
	  when T_HASH
		len,val = read_hash(input)
		yield T_HASH,val
		return 1+len
	  else
		raise IOError.new("Decoder: wrong header: #{header.to_s}")
	  end
	end

	private 

	#* input: IO object
	#* return: read buffer length and array object
	def read_array(input)
	  ctype,len = input.read(5).unpack("CV")
	  len = us2s(len,4)
	  case ctype
	  when T_NULL
		val = []
		len.times { val << nil }
		return 5,val
	  when T_BOOLEAN_TRUE
		val = input.read(len).unpack("C*")
		val.collect!{|i| T_BOOLEAN_TRUE==i ? true : false }
		return (len+5),val
	  when T_INTEGER1
		val = input.read(len).unpack("c*")
		return (len+5),val
	  when T_INTEGER2
		val = input.read(len*2).unpack("v*")
		return (len*2+5),us2s_array(val,2)
	  when T_INTEGER4
		val = input.read(len*4).unpack("V*")
		return (len*4+5),us2s_array(val,4)
	  when T_INTEGER8
		val = input.read(len*8).unpack("V*")
		ret = []
		len.times {
		  ret << ( val.shift | (val.shift << 32) )
		}
		return (len*8+5),us2s_array(ret,8)
	  when T_FLOAT
		val = input.read(len*4).unpack("e*")
		return (len*4+5),val
	  when T_DOUBLE
		val = input.read(len*8).unpack("E*")
		return (len*8+5),val
	  when T_STRING
		ret = []
		sz = 0
		len.times {
		  n = input.read(4).unpack("V").shift
		  if n == -1 || n == 0xffffffff
			ret << nil
			sz += 4
		  else
			ret << input.read(n)
			sz += (4+n)
		  end
		}
		return (sz+5),ret
	  when T_DECIMAL
		ret = []
		sz = 0
		len.times {
		  n = input.read(4).unpack("V").shift
		  if n == -1 || n == 0xffffffff
			ret << nil
			sz += 4
		  else
			a = input.read(n)
			ret << ( a["."] ? a.to_f : a.to_i )
			sz += n+4
		  end
		}
		return (sz+5),ret
	  else
		raise IOError.new("Decoder: wrong array type: #{type.to_s}")
	  end
	end

	#* input: IO object
	#* return: read buffer length and list object
	def read_list(input)
	  len = input.read(4).unpack("V").shift
	  ret = []
	  sz = 0
	  if len > 0 then
		len.times {
		  sz += read_block(input) {|ctype,val|
			case ctype
			when T_BOOLEAN_TRUE
			  ret << true
			when T_BOOLEAN_FALSE
			  ret << false
			when T_NULL
			  ret << nil
			else
			  ret << val
			end
		  }
		}
	  end
	  return len,ret
	end

	#* input: IO object
	#* return: read buffer length and hash object
	def read_hash(input)
	  len = input.read(4).unpack("V").shift
	  ret = Hash.new
	  sz = 0
	  if len > 0 then
		len.times {
		  key = nil
		  val = nil
		  sz += read_block(input) {|ctype,val|
			key = case ctype
				  when T_BOOLEAN_TRUE
					true
				  when T_BOOLEAN_FALSE
					false
				  when T_NULL
					nil
				  else
					val
				  end
		  }
		  sz += read_block(input) {|ctype,val|
			val = case ctype
				  when T_BOOLEAN_TRUE
					true
				  when T_BOOLEAN_FALSE
					false
				  when T_NULL
					nil
				  else
					val
				  end
		  }
		  ret[key] = val
		}
	  end
	  return len,ret
	end
	
  end #module Decoder


  #############################################################
  #  Transfer object
  #############################################################

  module MessageOutput
	include Encoder,Decoder

	def writes(a)
	  @out << a
	  return a.size
	end

    def exec_content(out,message_type)
	  @out = out
      bpos = write_int1(message_type) # message type

      write(@sid)
      yield

      @out.flush
      @out = nil
    end
  end

  class ResultOkObject
    include MessageOutput

	attr_reader :sid,:code

	def initialize(sid,value)
	  @sid = sid
	  @code = R_OK
	  @value = value
	end

	def exec(out)
      exec_content(out,M_RETURN) {
        write_int1(@code)
        write(@value)
      }
	end

    def value
      @value
    end
  end # class ResultOkObject

  class ResultErrObject
	include MessageOutput

	attr_reader :sid,:code,:err_klass,:err_message,:err_detail

	def initialize(sid,code,klass,message,detail)
	  @sid = sid
	  @code = code
	  @err_klass = klass
	  @err_message = message
	  @err_detail = detail
	end

	def exec(out)
      exec_content(out,M_RETURN) {
        write_int1(@code)
        write(@err_klass)
        write(@err_message)
        write(@err_detail)
      }
	end

    def value
      case @code
      when R_APP_ERROR
        raise RPCException.new(@err_klass,@err_message,@err_detail)
      when R_PROTOCOL_ERROR, R_FATAL_ERROR
        raise RPCException.new(@err_klass,@err_message,@err_detail)
      else
        raise IOError.new("Unknown return code: #{code}")
      end
    end
  end # class ResultErr

  class CallingOject
	include MessageOutput

	attr_reader :sid,:name,:args

	def build_by_sender(sid,name,args)
	  @sid = sid
	  @name = name
	  @args = args
	  raise IOError.new("(bug) Argument is not an instance of Array. #{args.class} : #{args}") unless @args.kind_of?(Array)
	  @args = [] if @args == nil
	  return self
	end

	def build_by_receiver(sid,input)
	  @sid = sid
	  t,@name = read(input)
	  raise IOError.new("Wrong calling message: name field is not string.") unless t == T_STRING
	  t,@args = read(input)
	  raise IOError.new("Wrong calling message: args field is not list.") unless t == T_LIST
	  return self
	end
	
	def exec(out)
      exec_content(out,M_CALL) {
		write(@name)
		write_list(@args)
      }
	end
  end # class CallingOject


  #############################################################
  #  Message server
  #############################################################

  class MessageServer
	include Decoder

	attr_reader :socket_state

	def dputs(a)
	  if @debug then
		puts "MS:#{@id} #{a}" 
		STDOUT.flush
	  end
	end

	def set_debug(b)
	  @debug = b
	end

	def initialize(id)
	  @id = id
	  @debug = false

	  @handler_table = Hash.new
	  @handler_lock = Monitor.new

	  @salt = rand(1000000000).to_s
	  @sid_counter = 0
	  @sid_lock = Monitor.new

	  @sending_queue = Queue.new
	  @receiving_table = Hash.new
	  
	  @socket_lock = Monitor.new
	  @socket_waiter = @socket_lock.new_cond
	  @socket_state = :socket_not_connected
	  @socket = nil

	  @receiving_thread = nil
	  @sending_thread = nil

	  @thread_pool = ThreadPool.new(4)
	end

	def add_handler(name,handler)
	  @handler_table[name] = handler
	end

	def set_socket(socket)
	  @socket_lock.synchronize do
		raise IOError.new("Wrong socket state: #{@socket_state.to_s}") if @socket_state != :socket_not_connected
		@socket = socket
		@socket_state = :socket_opened
		dputs ":ready for I/O stream."
	  end
	end

	def block_working_thread
	  return nil unless @socket_state == :socket_opened
	  dputs ":started working block."
	  @socket_lock.synchronize do
		@receiving_thread = Thread.start { receiver_loop_starter }
		@sending_thread = Thread.start { sender_loop_starter }
	  end
	  loop {
		break if @sending_thread.nil? || @receiving_thread.nil?
		@socket_lock.synchronize do
		  @socket_waiter.wait
		end
		dputs ":   working-thread wakeup: #{@sending_thread}"
	  }
	  dputs ":closing socket."
	  @socket_lock.synchronize do
		@socket.close
		@socket_state = :socket_not_connected
		@socket_waiter.broadcast
	  end
	  @sending_queue << nil
	  dputs ":finished working block."
	end

	def shutdown
	  dputs ":shutdown message arived."
	  @socket_lock.synchronize do
		return if @socket_state == :socket_not_connected
		@socket_state = :socket_closing
		@sending_queue.push nil # wakeup sender thread
	  end
	  dputs ":waiting for termination of threads..."
	  scount = 0
	  loop {
		break if @socket_state == :socket_not_connected
		@socket_lock.synchronize do
		  @socket_waiter.wait(0.5)
		end
		scount += 1
		if scount > 3 then
		  @socket.close
		  break
		end
	  }
	  if @sending_queue.size > 0 then
		dputs ": sending queue: #{@sending_queue.size} messages are remained."
	  end
	  dputs ":shutdowned."
	end

	def send_message(name,args)
	  raise IOError.new("Not connected... #{@socket_state.to_s}") unless @socket_state == :socket_opened
      sid = add_queue_calling_message(name,args)
      ret = @receiving_table[sid].pop
      if ret then
        @receiving_table.delete(sid)
        dputs ":  received result:  #{sid}"
        return ret.value
      else
        raise StandardError.new("BUG: the return object is nil. sid=#{sid}")
      end
	end

    private
	def add_queue_calling_message(name,args)
	  sid = get_sid
	  c = CallingOject.new.build_by_sender(sid,name,args)
      @receiving_table[sid] = Queue.new
	  @sending_queue.push c
	  dputs ": +Queue[#{@sending_queue.size}] : CALL : #{c.sid} -> #{name}"
	  return sid
	end

	def add_queue_result(sid,obj)
	  r = ResultOkObject.new(sid,obj)
	  @sending_queue.push r
	  dputs ": +Queue[#{@sending_queue.size}] : R_OK : #{r.sid}"
	end

	def add_queue_error(sid,code,klass,message,detail)
	  r = ResultErrObject.new(sid,code,klass,message,detail)
	  @sending_queue.push r
	  dputs ": +Queue[#{@sending_queue.size}] : R_ER : #{r.sid}"
	end
	
	def sender_loop_starter
	  begin
		sender_loop
	  ensure
		dputs ": Sender-thread finished"
		@sending_thread = nil
		@socket_lock.synchronize do
		  @socket_waiter.broadcast
		end
	  end
	end

	def sender_loop
	  loop {
		begin
          entry = @sending_queue.shift
		  if entry then 
            dputs ": -Queue[#{@sending_queue.size}] : #{entry.sid}"
			entry.exec(@socket)
			dputs ": sent a message : #{entry.sid}"
		  end
		rescue => evar
		  mes = evar.message
		  dputs "[senderloop] #{evar.to_s}  "
		  if mes["abort"] then
			dputs ": [sendloop] disconnected by remote host."
		  elsif evar.class == IOError then
			dputs evar.backtrace.join("\n")
			dputs ": [sendloop] try to reset the connection."
			@socket_lock.synchronize do
			  @socket_state = :socket_closing
			end
		  else
			dputs evar.backtrace.join("\n")
			dputs ": [sendloop] going to recover the communication."
		  end
		  if entry then
            @receiving_table[entry.sid].push ResultErrObject.new(entry.sid,R_PROTOCOL_ERROR,"IOError",evar.message,evar.backtrace.join("\n"))
		  end
		ensure
		  dputs ": [sendloop]--------------"
		end # rescue
		@socket_lock.synchronize do
		  if @socket_state == :socket_closing || 
			  @socket_state == :socket_not_connected then
			dputs ": sender-thread terminating..."
			return
		  end
		end
	  } # loop
	end

	def receiver_loop_starter
	  begin
		receiver_loop
	  ensure
		dputs ": Receiver-thread finished"
		@receiving_thread = nil
		@socket_lock.synchronize do
		  @socket_waiter.broadcast
		end
	  end
	end

    def build_result_object(sid,input)
      t,code = read(input)
      dputs ": code=#{code}"
      raise IOError.new("Wrong Result message: code field is not integer1.") unless t == T_INTEGER1
      case code
      when R_OK
        t,value = read(input)
        return ResultOkObject.new(sid,value)
      when R_APP_ERROR,R_PROTOCOL_ERROR,R_FATAL_ERROR
        t,err_klass = read(input)
        raise IOError.new("Wrong Result message: error-class field is not string. #{t}") unless t == T_STRING || t == T_NULL
        t,err_message = read(input)
        raise IOError.new("Wrong Result message: error-message field is not string. #{t}") unless t == T_STRING || t == T_NULL
        t,err_detail = read(input)
        raise IOError.new("Wrong Result message: error-detail field is not string. #{t}") unless t == T_STRING || t == T_NULL
        return ResultErrObject.new(sid,code,err_klass,err_message,err_detail)
      else
        raise IOError.new("Unknown return code: #{@code}")
      end
    end

	def receiver_loop
	  loop {
		begin
		  t,mcode = read(@socket)
		  dputs ": receiving a message : code=#{mcode}"
		  msid = nil
		  case mcode
		  when M_CALL
			t,msid = read(@socket)
			c = CallingOject.new.build_by_receiver(msid,@socket)
			dputs ": received: OK : #{c.sid}"
			received(c)
		  when M_RETURN
			t,msid = read(@socket)
            dputs ": sid=#{msid}"
			r = build_result_object(msid,@socket)
			dputs ": received: RET: #{r.sid}"
            @receiving_table[r.sid].push r
		  else
			dputs ": Unknown message code. try to reset the connection."
            @socket_state = :socket_closing
            @sending_queue.push nil # wakeup sender thread
			return
		  end # case
		  @socket_lock.synchronize do
			if @socket_state == :socket_closing then
			  dputs ": receiver-thread terminating..."
			  return
			end
		  end
		rescue Exception => evar
		  mes = evar.message
		  if msid then
            r = ResultErrObject.new(msid,R_PROTOCOL_ERROR,"ResultObjectError",mes,evar.backtrace.join("\n"))
            @receiving_table[msid].push r
		  end
		  dputs "[rcvloop] #{evar.to_s}"
		  if mes["close"] || mes["reset"] then
			dputs ": [rcvloop] disconnected by remote host."
			break
		  elsif evar.kind_of?(IOError) then
			dputs ": [rcvloop] try to reset the connection."
			@socket_lock.synchronize do
			  @socket_state = :socket_closing
			end
			break
		  else
			dputs ": [rcvloop] going to recover the communication."
			bt = evar.backtrace.join("\n")
			dputs ": [rcvloop] #{bt}"
		  end
		ensure
		  dputs ": [rcvloop]--------------"
		end # begin rescue
	  } # loop
	end

	def received(calling_obj)
	  handler = nil
	  @handler_lock.synchronize do
		handler = @handler_table[calling_obj.name]
	  end
	  if handler.nil? then
        add_queue_error(calling_obj.sid,R_PROTOCOL_ERROR,
                        "NoSuchMethodException",
                        "Not found the remote method #{calling_obj.name}.","")
		return
	  end
	  @thread_pool.invoke lambda {
		dputs ": methodInvodation: Start: #{calling_obj.name} : #{calling_obj.sid}"
		begin
		  ret = handler.call(*calling_obj.args)
          add_queue_result(calling_obj.sid,ret)
		rescue => evar
		  dputs ": exception was occured: #{evar.message}"
          add_queue_error(calling_obj.sid,R_APP_ERROR,evar.class.to_s,
                          evar.message,evar.backtrace.join("\n"))
		ensure
		  dputs ": methodInvodation: End  : #{calling_obj.name} : #{calling_obj.sid}"
		end # begin
	  } # lambda
	end

	def get_sid
	  @sid_lock.synchronize do
		@sid_counter += 1
		return "SBR:#{@salt}:#{@sid_counter}"
	  end
	end


  end # class MessageServer

  #############################################################
  #  Server and Client
  #############################################################
  
  class RPCCommunicator

	def initialize(id)
	  @message_server = MessageServer.new(id)
	end

	def dputs(a)
	  puts a if @debug
	end

	def set_debug(b)
	  @debug = b
	  @message_server.set_debug(b)
	end

	def add_handler(name,handler)
	  @message_server.add_handler(name,handler)
	end

	def send_message(name,*args)
	  @message_server.send_message(name,args)
	end

  end

  class RPCServer < RPCCommunicator

	def initialize(port = 0)
	  super("SV")
	  @server_socket = TCPServer.open(port)
	  @socket_thread = nil
	  @shutdown_flag = false
	end

	def get_port_number
	  @server_socket.addr[1]
	end

	def start
	  @shutdown_flag = false
	  @socket_thread = Thread.start do
		begin
		  accepter_loop
		ensure
		  dputs "RPCServer: acceptor-thread finished."
		  @socket_thread = nil
		end
	  end
	  Thread.pass
	end

	def shutdown
	  dputs "RPCServer: shutdown broadcast arrived."
	  @shutdown_flag = true
	  @message_server.shutdown
	  dputs "RPCServer: shutdown completed."
	end

    private
	def accepter_loop
	  dputs "RPCServer: start loop"
	  loop {
		break if @shutdown_flag
		dputs "RPCServer: waiting for client's connection..."
		@message_server.set_socket( @server_socket.accept )
		dputs "RPCServer: connection established."
		@message_server.block_working_thread
		dputs "RPCServer: disconnected."
	  }
	end

  end

  class RPCMultiServer

	def initialize(port = 0)
	  @server_socket = TCPServer.open(port)

	  @server_list = []
	  @server_list.extend(MonitorMixin)

	  @handlers = {}
	  @handlers.extend(MonitorMixin)

	  @accepter_hook = nil

	  @debug = false
	end

	# this hook object will be called before starting connection.
	# set_accepter_hook {|message_server|
	#   do something
	# }
	def set_accepter_hook(&block)
	  @accepter_hook = block
	end

	def dputs(a)
	  puts a if @debug
	end

	def set_debug(b)
	  @debug = b
	end

	def add_handler(name,handler)
	  @handlers.synchronize do
		@handlers[name] = handler
	  end
	end

	def get_port_number
	  @server_socket.addr[1]
	end

	def start
	  begin
		accepter_loop
	  ensure
		@server_list.synchronize do
		  @server_list.each {|i|
			i.shutdown
		  }
		end
		dputs "RPCMultiServer: finished."
	  end
	end

    private
	def accepter_loop
	  dputs "RPCMultiServer: start loop"
	  counter = 0
	  loop {
		dputs "RPCMultiServer: waiting for client's connection..."
		Thread.start( @server_socket.accept ) do |socket|
		  counter += 1
		  start_a_server(socket,counter)
		end
	  }
	end

	def start_a_server(socket,counter)
	  begin
		message_server = MessageServer.new("SV:#{counter}")
		message_server.set_debug(@debug)
		@accepter_hook.call(message_server) if @accepter_hook
		@handlers.synchronize do 
		  @handlers.each{ |k,v|
			message_server.add_handler(k,v)
		  }
		end
		message_server.set_socket( socket )
		dputs "RPCMultiServer#{counter}: connection established."
		@server_list.synchronize do
		  @server_list << message_server
		end
		message_server.block_working_thread
		@server_list.synchronize do
		  @server_list.delete(message_server)
		end
	  rescue => e
		dputs e.to_s
		dputs e.backtrace.join("\n")
	  ensure 
		socket.close if socket
		dputs "RPCMultiServer#{counter}: finished."
	  end
	end

  end
  
  class RPCClient < RPCCommunicator

	def initialize(host,port)
	  super("CL")
	  @host = host
	  @port = port
	  @socket_thread = nil
	  @shutdown_flag = false
	end

	def start
	  @shutdown_flag = false
	  @socket_thread = Thread.start do
		begin
		  connection_loop
		rescue => e
		  dputs e.to_s
		ensure
		  dputs "RPCClient: connection-thread finished."
		  @socket_thread = nil
		end
	  end
	  loop {
		Thread.pass
		break if @message_server.socket_state == :socket_opened
	  }
	end

	def shutdown
	  dputs "RPCClient: shutdown broadcast arrived."
	  @shutdown_flag = true
	  @message_server.shutdown
	  dputs "RPCClient: shutdown completed."
	end
	
    private
	def connection_loop
	  dputs "RPCClient: start loop"
	  loop {
		break if @shutdown_flag
        soc = TCPSocket.open(@host,@port)
		@message_server.set_socket( soc )
		dputs "RPCClient: connection established."
		@message_server.block_working_thread
		dputs "RPCClient: disconnected."
	  }
	end

  end

  #############################################################
  #  Exception
  #############################################################

  class RPCException < IOError

	attr_reader :klass,:detail

	def initialize(klass,message,detail)
	  @klass = klass
	  @message = message
	  @detail = detail
	end

	def message
	  return @message
	end

	alias :to_s :message

  end

  #############################################################
  #  Thread pool
  #############################################################

  class ThreadPool 

	def initialize(num)
	  @job_queue = Queue.new
	  @worker_threads = []
	  num.times {
		@worker_threads << Thread.start {
		  loop {
			job = @job_queue.shift
			break unless job
			job.call
		  }
		}
	  }
	end

	def invoke(job)
	  @job_queue.push(job)
	end

	def dispose
	  @worker_threads.size.times {
		invoke(nil)
	  }
	end

  end

end #module INOURPC
