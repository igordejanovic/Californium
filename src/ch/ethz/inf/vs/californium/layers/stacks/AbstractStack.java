/*******************************************************************************
 * Copyright (c) 2012, Institute for Pervasive Computing, ETH Zurich.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * 
 * This file is part of the Californium (Cf) CoAP framework.
 ******************************************************************************/
package ch.ethz.inf.vs.californium.layers.stacks;

import java.io.IOException;
import java.net.SocketException;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;

import ch.ethz.inf.vs.californium.coap.Message;
import ch.ethz.inf.vs.californium.layers.Layer;
import ch.ethz.inf.vs.californium.layers.UpperLayer;

/**
 * The Class AbstractStack.
 *
 * @author Francesco Corazza
 */
public abstract class AbstractStack extends UpperLayer {
	
	// Static Attributes ///////////////////////////////////////////////////////////
	
	/** The udp port. */
	protected int udpPort = 0; // TODO
	
	/** The run as daemon. */
	protected boolean runAsDaemon = true; // JVM will shut down if no user threads are running
	
	/** The transfer block size. */
	protected int transferBlockSize = 0;
	
	// Members /////////////////////////////////////////////////////////////////////
	
	/** The layer queue. */
	private final Deque<Layer> layerQueue = new LinkedBlockingDeque<Layer>();
	
	protected UpperLayer upperLayer = null;
	private ExecutorService threadPool;
	
	// Constructors /////////////////////////////////////////////////////////////////////
	
	/**
	 * 
	 * @param threadPool
	 * @param actualPort The local UDP port to listen for incoming messages
	 * @param daemon True if receiver thread should terminate with main thread
	 * @param defaultBlockSize The default block size used for block-wise transfers
	 *        or -1 to disable outgoing block-wise transfers
	 * @throws SocketException
	 */
	public AbstractStack(int udpPort, int transferBlockSize,
			boolean runAsDaemon, ExecutorService threadPool)
					throws SocketException {
		
		this.threadPool = threadPool;
		this.udpPort = udpPort;
		this.transferBlockSize = transferBlockSize;
		this.runAsDaemon = runAsDaemon;
		
		enquequeLayer(this);
		createStack();
	}
	
	protected abstract void createStack() throws SocketException;
	
	public AbstractStack(int udpPort, boolean runAsDaemon)
			throws SocketException {
		this(udpPort, 0, runAsDaemon, null);
	}
	
	public AbstractStack(boolean fake) {
	}
	
	public AbstractStack(int udpPort) throws SocketException {
		this(udpPort, true);
	}
	
	public AbstractStack() throws SocketException {
		this(0);
	}
	
	
	/**
	 * Sets the up port.
	 * 
	 * @param port
	 *            the new up port
	 */
	public void setupPort(int port) {
		this.udpPort = port;
		LOG.config(String.format("Custom port: %d", this.udpPort));
	}
	
	/**
	 * Sets the up transfer.
	 * 
	 * @param defaultBlockSize
	 *            the new up transfer
	 */
	public void setupTransfer(int defaultBlockSize) {
		this.transferBlockSize = defaultBlockSize;
		LOG.config(String.format("Custom block size: %d", this.transferBlockSize));
	}
	
	/**
	 * Sets the up deamon.
	 * 
	 * @param daemon
	 *            the new up deamon
	 */
	public void setupDeamon(boolean daemon) {
		this.runAsDaemon = daemon;
		LOG.config(String.format("Custom daemon option: %b", this.runAsDaemon));
	}
	
	/**
	 * @return the upperLayer
	 */
	public UpperLayer getUpperLayer() {
		return this.upperLayer;
	}
	
	protected synchronized void enquequeLayer(Layer layer) {
		// get the first layer if the stack is not empty
		if (!this.layerQueue.isEmpty()) {
			Layer firstLayer = this.layerQueue.peekFirst();
			
			// link the upper layer in the stack with the inserting layer
			if (firstLayer instanceof UpperLayer) {
				((UpperLayer) firstLayer).setLowerLayer(layer);
				LOG.config(firstLayer.getClass().getSimpleName()
						+ " has below "
						+ layer.getClass().getSimpleName());
			}
		}
		
		this.layerQueue.addFirst(layer);
	}
	
	
	public abstract int getPort();
	
	@Override
	protected void doSendMessage(Message msg) throws IOException {
		LOG.finest(this.getClass().getSimpleName() + " doSendMessage");
		
		// defensive programming before entering the stack, lower layers should assume a correct message.
		if (msg != null) {
			
			// check message before sending through the stack
			if (msg.getPeerAddress().getAddress() == null) {
				throw new IOException("Remote address not specified");
			}
			
			// delegate to first layer
			sendMessageOverLowerLayer(msg);
		}
	}
	
	@Override
	protected void doReceiveMessage(Message msg) {
		LOG.finest(this.getClass().getSimpleName() + " doReceiveMessage");
		
		// send the message to registered receivers
		if (this.threadPool != null) {
			this.threadPool.submit(new ReceiveRunnable(msg));
		} else {
			deliverMessage(msg);
		}
	}
	
	class ReceiveRunnable implements Runnable {
		
		private Message message;
		
		public ReceiveRunnable(Message message) {
			if (message == null) {
				throw new IllegalArgumentException("message == null");
			}
			this.message = message;
		}
		
		@Override
		public void run() {
			deliverMessage(this.message);
		}
	}
}