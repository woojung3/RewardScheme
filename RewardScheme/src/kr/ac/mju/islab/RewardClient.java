package kr.ac.mju.islab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kr.ac.mju.islab.RewardProto.RewardPacket;

/**
 * RewardClient class is used to communicate with RewardServer class;
 * Unless you have to modify protocols underlying RewardServer/Client communication,
 * it would be much easier to use RewardQuery, which is a wrapping class
 * of the RewardClient.
 * 
 * @author jwlee
 * @version 1.0.0
 * @since 2017-03-08
 */
public class RewardClient {
	public RewardPacket recvPacket;
	private AsynchronousChannelGroup group;
    public RewardClient(String host, int port, final RewardPacket packet) throws IOException, InterruptedException {
        //create a socket channel
        group = AsynchronousChannelGroup.withThreadPool(Executors.newSingleThreadExecutor());
        AsynchronousSocketChannel sockChannel = AsynchronousSocketChannel.open(group);
        
        //try to connect to the server side
        sockChannel.connect(new InetSocketAddress(host, port), sockChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel channel) {
				//write an message to server side
				startWrite(channel, packet);
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println("fail to connect to server");
                try {
					group.shutdownNow();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
        });

        // wait until group.shutdown()/shutdownNow(), or the thread is interrupted:
        group.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
   
    private void startWrite(final AsynchronousSocketChannel sockChannel, final RewardPacket packet) {
    	// Prepend packetLenInfo to the RewardPacket, so that the other side can determine the packet size
    	int packetLen = packet.toByteArray().length;
    	byte[] packetLenInfo = ByteBuffer.allocate(4).putInt(packetLen).array();

        ByteBuffer buf = ByteBuffer.allocate(4 + packetLen);
        buf.put(packetLenInfo);
        buf.put(packet.toByteArray());
        buf.flip();
        // END

        sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {
				//start to read message
				startRead(channel); 
			}

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println("Fail to send packet to server");
            }
        });
    }
    
    private void startRead(final AsynchronousSocketChannel sockChannel) {
        final ByteBuffer buf = ByteBuffer.allocate(32768);	// Capable of dealing with #100 aggregated receipt
        
        sockChannel.read(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>(){
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {   
            	// Process header to get a length of received packet.
            	byte[] packetLenInfo = new byte[4];
				buf.flip();
            	buf.get(packetLenInfo);
				buf.compact();
            	
				byte[] recvBytePacket = new byte[ByteBuffer.wrap(packetLenInfo).getInt()];
				if (recvBytePacket.length > 32768) {
					System.out.println("Buffer size is smaller than received packet size: " + recvBytePacket.length);
				}
				buf.flip();
				buf.get(recvBytePacket);
				buf.compact();
				// END

            	// Save received packet from server into public variable rtnPacket
				try {
					recvPacket = RewardPacket.parseFrom(recvBytePacket);
					sockChannel.close();
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
}
