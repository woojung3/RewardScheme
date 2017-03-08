package kr.ac.mju.islab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RewardClient {
	public String rtnPacket = "A";
	private AsynchronousChannelGroup group;
    public RewardClient(String host, int port, final String packet) throws IOException, InterruptedException {
        //create a socket channel
        group = AsynchronousChannelGroup.withThreadPool(Executors.newSingleThreadExecutor());
        AsynchronousSocketChannel sockChannel = AsynchronousSocketChannel.open(group);
        
        //try to connect to the server side
        sockChannel.connect(new InetSocketAddress(host, port), sockChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel channel) {
				//start to read message
				startRead(channel);
				//write an message to server side
				startWrite(channel, packet);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println("fail to connect to server");
            }
            
        });

        // wait until group.shutdown()/shutdownNow(), or the thread is interrupted:
        group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
   
    private void startRead(final AsynchronousSocketChannel sockChannel) {
        final ByteBuffer buf = ByteBuffer.allocate(2048);
        
        sockChannel.read(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>(){

            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {   
            	// Save return packet from server into public variable rtnPacket
                rtnPacket = new String(buf.array());
				try {
					group.shutdownNow();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println("fail to read message from server");
            }
            
        });
        
    }
    private void startWrite(final AsynchronousSocketChannel sockChannel, final String message) {
        ByteBuffer buf = ByteBuffer.allocate(2048);
        buf.put(message.getBytes());
        buf.flip();
        sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) { }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println("Fail to send packet to server");
            }
        });
    }
    
    public static void main(String[] args) {
    	String packet = "echo test";
		try {
			System.out.println((new RewardClient("127.0.0.1", 3575, packet)).rtnPacket);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
    }
}
