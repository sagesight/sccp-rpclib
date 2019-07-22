package com.goodforgoodbusiness.rpclib.stream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channels;
import java.nio.channels.Pipe.SourceChannel;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.WriteStream;

/**
 * Uses {@link PipedWriteStream} to create an {@link InputStream} that has basic flow controls.
 */
public class InputWriteStream extends PipedWriteStream implements WriteStream<Buffer> {
	// the underlying stream around the channel
	private final InputStream channelStream;
	
	// the inputstream we create with flow controls
	private final InputStream inputStream;
	
	private int maxWriteQueueSize = 1000;
	private int available = 0;
	
	private boolean writeQueueFull = false;
	
	private Handler<Throwable> exceptionHandler = null;
	private Handler<Void> drainHandler = null;
	
	public InputWriteStream() throws IOException {
		super();
		
		this.channelStream = Channels.newInputStream(super.getSource());
		
		// wrap with some flow control
		// whenever we're about to go to a blocking read, and don't have enough
		// indicate we want more data
		this.inputStream = new InputStream() {
			@Override
			public int available() throws IOException {
				return available;
			}
			
			@Override
			public int read() throws IOException {
				try {
					var b = channelStream.read();
					if (b >= 0) {
						updateAvailable(-1);
						return b;
					}
					else {
						return -1;
					}
				}
				catch (AsynchronousCloseException e) {
					close();
					return -1;
				}
				catch (IOException e) {
					if (exceptionHandler != null) {
						exceptionHandler.handle(e);
					}
					
					close();
					return -1;
				}
			}
			
			@Override
			public int read(byte [] b, int off, int len) throws IOException {
				try {
					var count = channelStream.read(b, off, len);
					if (count >= 0) {
						updateAvailable(-count);
						return count;
					}
					else {
						close();
						return -1;
					}
				}
				catch (IOException e) {
					if (exceptionHandler != null) {
						exceptionHandler.handle(e);
					}
					
					close();
					return -1;
				}
			}
			
			@Override
			public void close() throws IOException {
				super.close();
				channelStream.close();
			}
		};
	}
	
	private void updateAvailable(int delta) {
		available += delta;
		if (available < maxWriteQueueSize / 2) {
			writeQueueFull = false;
			if (drainHandler != null) {
				this.drainHandler(null);
			}
		}
		else if (available < maxWriteQueueSize) {
			writeQueueFull = false;
		}
		else {
			writeQueueFull = true;
		}
	}
	
	/**
	 * Block this because wrapped in {@link InputStream}.
	 */
	@Override
	public SourceChannel getSource() {
		throw new UnsupportedOperationException();
	}
	
	/**
	 * Returns the flow controlled {@link InputStream} wrapping the {@link SourceChannel}
	 * @return
	 */
	public InputStream getInputStream() {
		return inputStream;
	}
	
	@Override
	public boolean writeQueueFull() {
		return this.writeQueueFull;
	}
	
	@Override
	public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
		this.maxWriteQueueSize = maxSize;
		return this;
	}
	
	@Override
	public WriteStream<Buffer> write(Buffer data, Handler<AsyncResult<Void>> handler) {
		updateAvailable(data.length());
		return super.write(data, handler);
	}
	
	@Override
	public WriteStream<Buffer> drainHandler(Handler<Void> handler) {
		this.drainHandler = handler;
		super.drainHandler(handler);
		return this;
	}
	
	@Override
	public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
		this.exceptionHandler = handler;
		super.exceptionHandler(handler);
		return this;
	}
}
