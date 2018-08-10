package tech.threekilogram.messengers;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 该类用于两个类之间通信 用于发送一个通知
 * 主要用于后台任务处理完成之后,继续下一步任务,
 * 使用handler:
 * 1.解决方法栈过长,
 * 2.可以切换到主线程
 *
 * @author wuxio 2018-05-01:20:16
 */
public class Messengers {

      /**
       * 用来生成index
       */
      private static final AtomicInteger MESSAGE_INDEX = new AtomicInteger();
      /**
       * 使用一个{@link HandlerThread}单独处理消息
       */
      private static ReceiveHandler sSendHandler;
      /**
       * 使用主线程处理消息
       */
      private static ReceiveHandler sMainHandler;

      static {

            /* 初始化自己 */
            init();
      }

      /**
       * 初始化变量
       */
      private static void init ( ) {

            /* 防止多次初始化 */

            if( sSendHandler != null && sMainHandler != null ) {
                  return;
            }

            HandlerThread thread = new HandlerThread( "Messengers" );
            thread.start();

            sSendHandler = new ReceiveHandler( thread.getLooper() );
            sMainHandler = new ReceiveHandler( Looper.getMainLooper() );
      }

      /**
       * 释放所有资源,未执行的任务将直接移除
       */
      public static void removeAllMessage ( ) {

            sSendHandler.removeCallbacksAndMessages( null );
            sMainHandler.removeCallbacksAndMessages( null );
      }

      /**
       * 发送一条空白消息
       *
       * @param what 标识,如果是奇数,发送到主线程,如果时偶数,发送到后台线程处理,注意不要使用0作为标识
       * @param who 发送给谁
       */
      public static void send ( int what, @NonNull OnMessageReceiveListener who ) {

            send( what, 0, null, who );
      }

      /**
       * 发送一条空白消息,携带一个数据
       *
       * @param what 标识,如果是奇数,发送到主线程,如果时偶数,发送到后台线程处理,注意不要使用0作为标识
       * @param who 发送给谁
       */
      public static void send ( int what, Object extra, @NonNull OnMessageReceiveListener who ) {

            send( what, 0, extra, who );
      }

      /**
       * 发送一条消息,携带一个数据
       *
       * @param what 标识,如果是奇数,发送到主线程,如果时偶数,发送到后台线程处理,注意不要使用0作为标识
       * @param delayed 延时
       * @param extra 额外的信息
       * @param who 发送给谁
       */
      public static void send (
          int what,
          int delayed,
          Object extra,
          @NonNull OnMessageReceiveListener who ) {

            final int judge = 2;
            ReceiveHandler sendHandler;

            /* 判断消息发送到哪个线程 */

            if( what % judge == 0 ) {
                  sendHandler = sSendHandler;
            } else {
                  sendHandler = sMainHandler;
            }

            Message obtain = Message.obtain();
            int key = MESSAGE_INDEX.addAndGet( 1 );
            sendHandler.MESSAGE_HOLDER_ARRAY.put( key, new Holder( what, extra, who ) );

            /* handler 使用该标识获取holder */
            obtain.arg1 = key;
            /* 用户设置的消息标识 */
            obtain.what = what;

            sendHandler.sendMessageDelayed( obtain, delayed );
      }

      /**
       * 发送一条空白消息
       *
       * @param what 标识,如果是奇数,发送到主线程,如果时偶数,发送到后台线程处理,注意不要使用0作为标识
       * @param who 发送给谁
       */
      public static void send ( int what, int delayed, @NonNull OnMessageReceiveListener who ) {

            send( what, delayed, null, who );
      }

      //============================ 移除一条消息监听 ============================

      /**
       * 移除一条消息
       *
       * @param what message 标识
       * @param listener 该消息发送给谁
       */
      public static void remove ( int what, OnMessageReceiveListener listener ) {

            final int judge = 2;
            if( what % judge == 0 ) {
                  clearListener( what, listener, sSendHandler );
            } else {
                  clearListener( what, listener, sMainHandler );
            }
      }

      /**
       * clear the listener , so the message have no receiver ,make him not receive message
       *
       * @param what message what
       * @param listener listener
       * @param handler which handler listener at
       */
      private static void clearListener (
          int what, OnMessageReceiveListener listener, ReceiveHandler handler ) {

            SparseArray<Holder> array = handler.MESSAGE_HOLDER_ARRAY;
            int size = array.size();
            for( int i = 0; i < size; i++ ) {

                  int key = array.keyAt( i );
                  Holder holder = array.get( key );

                  if( holder != null && holder.what == what && holder.listener.get() == listener ) {
                        holder.listener.clear();

                        /* 不移除原因:因为线程不安全,防止正在处理消息时空指针异常,在处理消息时,会自动处理该holder */
                  }
            }
      }

      //============================ send messenger async ============================

      /**
       * 接收{@link #send(int, OnMessageReceiveListener)}发送的消息,然后转发给监听消息{@link
       * OnMessageReceiveListener}
       */
      private static class ReceiveHandler extends Handler {

            /**
             * 保存需要转发的消息
             */
            final SparseArray<Holder> MESSAGE_HOLDER_ARRAY = new SparseArray<>();

            /**
             * 不同的loop 消息送到不同的线程
             *
             * @param looper looper
             */
            private ReceiveHandler ( Looper looper ) {

                  super( looper );
            }

            /**
             * 处理消息
             */
            @Override
            public void handleMessage ( Message msg ) {

                  /* 此处处理发送消息的工作 */

                  SparseArray<Holder> holderArray = MESSAGE_HOLDER_ARRAY;
                  Holder holder = holderArray.get( msg.arg1 );
                  if( holder != null ) {

                        OnMessageReceiveListener listener = holder.listener.get();
                        if( listener != null ) {

                              Object extra = holder.extra;
                              listener.onReceive( holder.what, extra );

                              /* when holder is not null, must delete it */
                              holderArray.delete( msg.arg1 );
                        }
                  }
            }
      }

      //============================ holder ============================

      /**
       * 记录message信息
       */
      private static class Holder {

            /**
             * 消息标识
             */
            private int    what;
            /**
             * 消息携带的额外信息
             */
            private Object extra;

            /**
             * 使用弱引用,防止泄漏
             */
            private WeakReference<OnMessageReceiveListener> listener;

            Holder ( int what, Object extra, OnMessageReceiveListener listener ) {

                  this.what = what;
                  this.extra = extra;
                  this.listener = new WeakReference<>( listener );
            }
      }
}
