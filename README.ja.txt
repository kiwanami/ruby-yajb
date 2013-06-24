yajb README
============

yajb は pure Ruby, pure Java によるRuby/Java連携実装です。


必要環境
--------

  * Ruby 1.8
  * Java2SE 1.4 以上の Java 実行環境

インストール方法
----------------

  コマンドラインで以下のように打ち込んでください。
  UNIX 系 OS ではおそらく root 権限が必要になります。

   ($ su)
    # ruby setup.rb

  インストール先を変更したりすることもできます。
  その場合は ruby setup.rb --help を実行してみてください。


簡単な使い方とかメモとか
----------------

○設定

javaコマンドへのパスが通っていれば特に設定はせずに使えるはずです。
追加の設定が必要であれば以下の定数をAPIが呼ばれる前に定義します。
必要なものだけ選ぶことができます。下に書いてある値はデフォルト値です。

JBRIDGE_OPTIONS = {
  :classpath => ["$CLASSPATH or %CLASSPATH%"],
                                # `echo #{classpath}` として評価された値が用いられます。
                                # 動作環境に応じて適切なパス区切り文字でjoinされます。
  :jvm_path =>  "java",         # javaコマンドへのパスを指定します。
                                # nilであれば新たなJVMを起動しません。
  :jvm_vm_args => "",           # VMオプションを指定します。 例: "-Xmx128m"
  :jvm_stdout => :t,            # JVMの標準出力の処理方法
                                # nil    : JVMの標準出力を拾うけども表示しない
                                # :t     : JVMの標準出力をRubyの標準出力に出力する
                                # :never : 標準出力を放置する
                                # Cygwin 以外の Windows環境では :never にしないと
                                # 動かないことがあります。
  :bridge_log => false,         # jbridgeのログ出力。 true or false

  :bridge_driver => :bstream,    # 通信ドライバ (:xmlrpc or :bstream)
  :bridge_server => :self,       # 接続先のブリッジサーバー
                                 # :self   : 自分でブリッジサーバーを立ち上げる。
                                 #           終了時はブリッジも終了させる。
                                 #           
                                 # "(address)"  : 指定したサーバーを使う。
                                 #           終了時は単にサーバーから切断する。

  # :xmlrpc用の設定
  :xmlrpc_bridge_port_r2j => 9010,  # 通信ポート番号: ruby -> java
  :xmlrpc_bridge_port_j2r => 9009,  # 通信ポート番号: java -> ruby
  :xmlrpc_bridge_opened => :kill,
                   # 指定されたポート(r2j)が使用中の場合の対応
                   # :kill  : jbridgeのEXITコマンドを送信して再起動を試みる
                   # :abort : プログラムを中断する
                   # :reuse : そのポートの再利用を試みる

  # :bstream用の設定
  :bstream_bridge_port => nil,  
                   # :bstream用の通信ポート番号
                   # nilの場合は空いているポートから勝手に選ぶ
}

・:xmlrpcドライバ

  とりあえず上位プロトコルの検証のために最初に作った通信ドライバ。XMLRPC はJava 
  にもRubyにも実装が存在し、APIも使いやすい一方、速度が遅く、数値についてはテキ
  ストを経由するので浮動小数点の精度が落ちる。将来別の言語とのブリッジを実装する
  ときに使用したり、トラブル時に問題を切り分けるときに使用する。

・:bstreamドライバ

  XMLRPCの速度の遅さや精度の問題を改善するために、独自バイナリーエンコーディング
  のRPCスタックから実装した通信ドライバ。 :xmlrpc よりも約2倍程度速い。

○使い方やAPI

とりあえず、

require 'yajb'
include JavaBridge

して、以下の4つのメソッドを呼ぶことでJavaのオブジェクトを生成、操作できます。

・jimport "java.util.*"
    ・文字列でインポート（省略）したいパッケージ名もしくはクラス名を書きます。
    ・後に書いたものが優先されます。
    ・カンマで区切って複数列挙できます。

・jnew("class name", constructor arguments ...)
    ・単純にJavaのインスタンスを生成します。
    ・以後Rubyの構文で、Javaのpublicメソッドもしくはpublicフィールドが参照、
      代入ができます。
    ・:java_util_ArrayList.jnew のように、シンボルを使って生成も出来ます。

・jextend("class or interface names", constructor arguments ...)
    ・カンマで列挙されたクラスやインタフェースを仮実装したインスタンスを生成します。
    ・特異メソッドでJavaのpublic/protectedメソッドをオーバーライドできます。
      注意点として、同じ名前のメソッドはまとめてオーバーライドされます。
    ・オーバーライドしたメソッドの中からは、protectedなメソッドも呼べます。
    ・superキーワードで、Javaのスーパークラスの同名のメソッドを呼べます。
    ・:java_awt_event_ActionListener.jext のように、シンボルを使って生成も出来ます。
    ・obj.jdef(:method_name) {|a,b,c| do something... } という方法でも実装できます。
    ・以下のようなブロックを与えると、デフォルトのメソッド実装を与えられます。
      jextend(:ActionListener) {|name,args|
          do somethind...
      }
      ここで、 name は呼ばれたメッセージ名、args は引数です。
      実装すべきメソッドが1,2個の場合に便利です。
      注意点として、これらのブロックを用いた方法で実装すると super や Java側の
      オブジェクトのprotected メソッドを呼べません。そのような場合は特異メソッドを
      使ってください。
    ・このメソッドで作ったオブジェクトは自動でGCされません。大量に使い捨てる場合は
      後述の junlink でオブジェクトをGCする必要があります。

・jstatic("class name") 
    Javaクラスへの静的参照を生成します。
    定数やスタティックメソッドにアクセスしたいときに使います。
    :javax_swing_JOptionPane.jclass のようにシンボルを使って生成も出来ます。

他にも以下のような補助メソッドがあります。

・stop_thread : GUIのイベントを待つなど、メインスレッドを止めたいときに使います
・wakeup_thread : 上で止めたメインスレッドを再開するときに使います
・junlink : 手動で指定されたオブジェクトの参照を削除します。
・jdump_object_list : Java側で保持されているオブジェクトの一覧を返します。

○やり取りされるオブジェクト

基本的に数値・文字列・配列はそのままの精度をなるべく維持して値としてやり取りされます。
具体的には以下のような感じです。

    Java                Ruby
 ----------------------------------------
   byte(Byte)          
   short(Short)        
   int(Integer)        Fixnum,Bignum,Float
   long(Long)            必要に応じて自動で変換される
   float(Float)        
   double(Double)      
   BigDecimal          
   String              String
   Object[]            Array
   基本型配列          Array : 型指定付き

数値系は、Javaの型と渡そうとしている値の大きさを動的に評価して
自動で変換を試みます。

RubyからJavaに転送できるのは上の基本型と代理オブジェクト(後述)のみです。
それ以外のオブジェクトを引数であたえた場合、IOErrorが発生します。

JavaからRubyには任意の値やオブジェクトを転送することが出来ます。
上の基本型とそのラッパーオブジェクトは値としてRubyに渡されます。
それ以外の一般のオブジェクトは代理オブジェクトがRuby側に生成されて渡されます。

RubyからJavaの配列渡しは Object[]、もしくは中身の型が全て一致していれば（例えば
全て文字列など）、適当な配列型を生成します。ただ、やっぱり自動ではヒントが少なす
ぎて思ったとおりに配列が生成できず、またプリミティブ型の配列を生成できないという
問題もあります。そこで、型を明示的に指定してJavaに渡す仕組みを用意しました。

例えば、int型の配列で渡したいときには以下のようにします。

 [:t_int, 1,2,3,4,5] 

Java側では、次のような配列が渡ります。

int[] array = new int[]{ 1,2,3,4,5 };

配列型指定シンボルは以下のものが使えます。

  :t_int1, :t_int2, :t_int4, :t_int8, :t_float, :t_double,
  :t_decimal, :t_string, :t_boolean

○代理オブジェクトとガベージコレクション(GC)

Rubyから参照されるJavaオブジェクトは、一意なIDが振られてJavaBridge内の
リポジトリで管理されます。また、Ruby側では対応する代理オブジェクトが生成されて、
Ruby側でJavaオブジェクトのふりをします。具体的には、代理オブジェクトに無いメソッ
ドが method_missing でキャッチされて、Java側に転送されます。代理オブジェクトが
Ruby側でGCされるとその情報がJava側にも伝えられて、Java側でもGCされるように参照を
削除します。

ただし、jextendで生成された代理オブジェクトはいつ削除するべきか自動で判断できないの
で、junlink を使って手動で削除する必要があります。

○文字エンコーディング

RubyとJavaの間でやりとりされる文字列は UTF-8 が使われます。RubyからJavaに
送られる文字列は UTF-8 でエンコーディングしておく必要があります。また、Java
からRubyに送られる文字列は UTF-8 でエンコーディングしてありますので、必要に応じ
て別のエンコーディングに変換する必要があります。

○スレッド

任意のスレッドからjbridgeのAPIを呼び出すことが出来ます。ただし、ファイナライザー
処理の中から呼び出すとデッドロックを引き起こすことがあります。

ネストされた呼び出しは、スレッドを再利用します。具体的には以下のようです。
JavaクラスのメソッドをRuby側でjextendを使ってオーバーライドして、オーバーライド
したメソッドの中からさらにJava側の処理を呼び出す状況を考えます。つまり、GUIイベ
ントなどでJavaからRubyに処理が回って来て、さらにその処理の中でダイアログや描画な
どのJavaのクラスを呼び出すような場合です。このとき、Java->Ruby->Java のように
Java側に再度処理が回って来たとき、新たなスレッドでその処理を行うのではなくて、始
めにRubyを呼び出してブロックしているスレッドを起こしてそのスレッドに処理を行わさ
せます。したがって、AWTのイベントをRubyで実装しても、Javaで実装したときと同じよ
うに動作します。このスレッド再利用の仕組みがないと、Ruby側でイベントを受けても
AWTのスレッドがブロックしているため、期待したとおりに動かなかったり、デッドロッ
クします。詳しくは、Swing とスレッドについて解説してある文書を参照してください。

[Threads and Swing] http://java.sun.com/products/jfc/tsc/articles/threads/threads1.html

Rubyのメインスレッドが終了するとプログラム自体が終了するのでJVMも終了してしまい
ます。GUI等でイベントを待ち受けたりするためにはGUI構築後にメインスレッドを止める
必要があります。簡単のために stop_thread と wakeup_thread が用意されています。
GUI を終了させる時には wakeup_thread を呼んだ方がいいみたいです。

※参照：sample/gui_〜.rb

○例外

Rubyから呼ばれたJavaプログラム内で例外が起きた場合、呼び出したRuby側にJava の例
外をラップした JException が投げられます。 JException には、Java で起きた例外の
クラス名、メッセージ、スタックトレースが含まれています。

Java から呼ばれた Ruby プログラム内で例外が起きた場合、呼び出した Java 側にRuby 
の例外をラップした jbridge.RemoteRuntimeException が投げられます。こちらの例外オ
ブジェクトにも、Ruby 側で発生した例外のクラス名、メッセージ、スタックトレースが
含まれています。

Java と Ruby の間で通信上の問題が起きた場合、IOError(IOException) もしくはそのサ
ブクラスが投げられます。

○速度

現在の実装はネットワークで引数や返り値を転送していますので、どうしても通常のRuby
オブジェクトのメソッド呼び出しより遅くなります。

遅さは主にデータの転送に起因しているため、呼び出し回数を減らすようなプログラミン
グをすれば改善されます。具体的には、取得したオブジェクトをインスタンス変数などで
必要な間保持するようにするなどが有効です。

また、動的 Java プログラミングの jlambda や JClassBuilder などを利用して、 Java 
でプログラムするようにすれば、呼び出しと返り値のみの通信になりますので、まとまっ
た処理をある程度高速化することが出来ます。

将来的に実装を JNI でも動くようにすれば、ある程度の速度の改善が出来るのではない
かと思っています。

○独自クラスローダー

JavaのClassLoaderオブジェクトをRuby側で実装した場合、yajbシステムでそ
のクラスローダーを使用するように登録する必要があります。 実装した 
ClassLoader オブジェクトをjadd_classloader メソッドで登録すると、登録
されたクラスローダーがそれ以降のクラス解決にて使用されるようになります。

※参照：sample/classfile.rb

○jlambda、動的Javaプログラミング

内部で Javassist を利用しているので、 jbridge 経由で Javassist を普通に利用する
ことが出来ます。 Java では必要だったキャストが Ruby では要らなくなるので、こうい
う動的プログラミングは大変楽なのですが、もう少し便利に使えるように、 Ruby の 
lambda の用に使える jlambda と、クラスをスクラッチから生成するユーティリティの 
JClassBuilder を作ってみました。 JClassBuilder などはかなりやっつけなので、もう
すこし便利な設計が出来ると思います。

また、これらのユーティリティは Javassit のコンパイラに依存しているため、その制限
を受けます。具体的には、コメントが書けいないとか、インナークラスが使えないとか、
ラベルつきジャンプ(break,continue)が使えないなどです。詳しくは、 Javassist のド
キュメントを参照してください。

※参照：sample/jlambda.rb

○その他

rdoc で生成した簡単なドキュメントが rdoc ディレクトリにあります。
簡単なサンプルが sample ディレクトリにあります。

まだまだバグがたくさんあると思いますが、
バグを発見した場合はご連絡いただければありがたいです。

現在は速度がやはり問題なので、どなたかチューニング作業などを
やっていただけるとありがたいです。

ライセンス
----------

  yajb自体は LGPL に従って配布します。

  XercesJ, xmlrpc は Apache Software License に従って配布します。
    This product includes software developed by the
    Apache Software Foundation (http://www.apache.org/).

  Javassist は千葉滋氏の著作物で MPL に従います。
   http://www.csg.is.titech.ac.jp/~chiba/javassist/

-----------------------------------------
Masashi Sakurai <m.sakurai@dream.com>
