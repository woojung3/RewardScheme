package kr.ac.mju.islab;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.google.protobuf.InvalidProtocolBufferException;

import kr.ac.mju.islab.RewardProto.RewardPacket;

/**
 * RewardAndroidClient class is a port of RewardClient for Android.
 * Its usage is same as RewardClient. Check jDoc for RewardClient for more details.
 * 
 * @author jwlee
 * @version 1.0.0
 * @since 2017-03-08
 */
public class RewardAndroidClient {
	public RewardPacket recvPacket;
    public RewardAndroidClient(final String host, final int port, final RewardPacket packet) throws IOException, InterruptedException {
    	Thread one = new Thread() {
    		public void run() {
				SocketChannel sockChannel = null;
				try {
					sockChannel = SocketChannel.open();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				try {
					sockChannel.connect(new InetSocketAddress(host, port));
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				// Prepend packetLenInfo to the RewardPacket, so that the other side can determine the packet size
				int packetLen = packet.toByteArray().length;
				byte[] packetLenInfo = ByteBuffer.allocate(4).putInt(packetLen).array();

				ByteBuffer buf = ByteBuffer.allocate(4 + packetLen);
				buf.put(packetLenInfo);
				buf.put(packet.toByteArray());
				buf.flip();
				// END
				
				// Write
				while (buf.hasRemaining()) {
					try {
						sockChannel.write(buf);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				final ByteBuffer recvBuf = ByteBuffer.allocate(32768);	// Capable of dealing with #100 aggregated receipt
				int bytesRead = -1;
				while (bytesRead == 0 | bytesRead == -1) {
					// Read
					try {
						bytesRead = sockChannel.read(recvBuf);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				// Process header to get a length of received packet.
				byte[] recvPacketLenInfo = new byte[4];
				recvBuf.flip();
				recvBuf.get(recvPacketLenInfo);
				recvBuf.compact();
				
				byte[] recvBytePacket = new byte[ByteBuffer.wrap(recvPacketLenInfo).getInt()];
				if (recvBytePacket.length > 32768) {
					System.out.println("Buffer size is smaller than received packet size: " + recvBytePacket.length);
				}
				recvBuf.flip();
				recvBuf.get(recvBytePacket);
				recvBuf.compact();
				// END

				try {
					recvPacket = RewardPacket.parseFrom(recvBytePacket);
				} catch (InvalidProtocolBufferException e) {
					e.printStackTrace();
				}
				
				try {
					sockChannel.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				Thread.currentThread().interrupt();
    		}
    	};
    	one.start();
    	one.join();
    }
}
