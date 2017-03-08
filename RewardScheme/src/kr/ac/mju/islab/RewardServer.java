package kr.ac.mju.islab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RewardServer {
    public RewardServer(String bindAddr, int bindPort) throws IOException, InterruptedException {
        InetSocketAddress sockAddr = new InetSocketAddress(bindAddr, bindPort);
        
        //create a socket channel and bind to local bind address
        AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(Executors.newSingleThreadExecutor());
        AsynchronousServerSocketChannel serverSock =  AsynchronousServerSocketChannel.open(group).bind(sockAddr);
        
        //start to accept the connection from client
        serverSock.accept(serverSock, new CompletionHandler<AsynchronousSocketChannel,AsynchronousServerSocketChannel>() {

            @Override
            public void completed(AsynchronousSocketChannel sockChannel, AsynchronousServerSocketChannel serverSock) {
                //a connection is accepted, start to accept next connection
                serverSock.accept(serverSock, this);
                //start to read message from the client
				startRead(sockChannel);
            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
                System.out.println("Fail to accept a connection");
            }
        } );
        
        // wait until group.shutdown()/shutdownNow(), or the thread is interrupted:
        group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
    
    private void startRead(AsynchronousSocketChannel sockChannel) {
        final ByteBuffer buf = ByteBuffer.allocate(2048);
        
        //read message from client
        sockChannel.read(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

            /**
             * When some message is read from client, this callback will be called
             */
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
                buf.flip();
                
                // Whatever response to the client
                startWrite(channel, buf);
                
                // Start to read next message again
                startRead(channel);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel ) { }
        });
    }
    
     private void startWrite(AsynchronousSocketChannel sockChannel, final ByteBuffer buf) {
         sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {

             @Override
             public void completed(Integer result, AsynchronousSocketChannel channel) { }

             @Override
             public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                 System.out.println( "Fail to send packet to client");
             }
             
         });
     }
     
     public static void main(String[] args) {
        try {
            new RewardServer("127.0.0.1", 3575);
        } catch (Exception ex) {
            Logger.getLogger(RewardServer.class.getName()).log(Level.SEVERE, null, ex);
        }
     }
}
