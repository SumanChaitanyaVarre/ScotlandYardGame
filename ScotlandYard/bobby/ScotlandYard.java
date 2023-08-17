package bobby;

import java.net.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.Semaphore;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ScotlandYard implements Runnable{

	/*
		this is a wrapper class for the game.
		It just loops, and runs game after game
	*/

	public int port;
	public int gamenumber;

	public ScotlandYard(int port){
		this.port = port;
		this.gamenumber = 0;
	}

	public void run(){
		while (true){
			Thread tau = new Thread(new ScotlandYardGame(this.port, this.gamenumber));
			tau.start();
			try{
				tau.join();
			}
			catch (InterruptedException e){
				return;
			}
			this.gamenumber++;
		}
	}

	public class ScotlandYardGame implements Runnable{
		private Board board;
		private ServerSocket server;
		public int port;
		public int gamenumber;
		private ExecutorService threadPool;

		public ScotlandYardGame(int port, int gamenumber){
			this.port = port;
			this.board = new Board();
			this.gamenumber = gamenumber;
			try{
				this.server = new ServerSocket(port);
				System.out.println(String.format("Game %d:%d on", port, gamenumber));
				server.setSoTimeout(5000);
			}
			catch (IOException i) {
				return;
			}
			this.threadPool = Executors.newFixedThreadPool(10);
		}


		public void run(){

			try{
			
				//INITIALISATION: get the game going

				
				
				Socket socket = null;
				boolean fugitiveIn;

				fugitiveIn = false;

				this.board.threadInfoProtector.acquire();

				this.board.dead = false;

				this.board.threadInfoProtector.release();
				/*
				listen for a client to play fugitive, and spawn the moderator.
				
				
				here, it is actually ok to edit this.board.dead, because the game hasn't begun
				*/
				do{
					
                    try {

						socket = this.server.accept();
					
						fugitiveIn = true;
					}
					catch (SocketTimeoutException t){

						continue;
					}
      
				} while (!fugitiveIn);
				
				System.out.println(this.gamenumber);

				// Spawn a thread to run the Fugitive

				ServerThread server_Thread = new ServerThread(this.board, -1, socket, this.port, this.gamenumber);
				
				this.threadPool.execute(server_Thread);

				this.board.threadInfoProtector.acquire();

				this.board.totalThreads++;
                     
				this.board.threadInfoProtector.release();
                                                            
				// Spawn the moderator
				Moderator mod = new Moderator(this.board);

				Thread mod_thread = new Thread(mod);

				mod_thread.start();
				
				//this.threadPool.execute(mod_thread);

				this.board.barrier2.release();

                                                  
				while (true){
					/*
					listen on the server, accept connections
					if there is a timeout, check that the game is still going on, and then listen again!
					*/
					Socket d_socket = null;
					try {

						if(!this.board.dead){

							d_socket = server.accept();

						}

					}

					catch (SocketTimeoutException t){
                                                                                       
						continue;

					}
					
					/*
					acquire thread info lock, and decide whether you can serve the connection at this moment,

					if you can't, drop connection (game full, game dead), continue, or break.

					if you can, spawn a thread, assign an ID, increment the totalThreads

					don't forget to release lock when done!
					*/
					                                         
                   // this.board.threadInfoProtector.acquire();

					int id = this.board.getAvailableID();

					if(id == -1 || this.board.dead){
						break;
					}
					//this.board.installPlayer(id);

					ServerThread d_server_Thread = new ServerThread(this.board, id, d_socket, this.port, this.gamenumber);
					
					this.threadPool.execute(d_server_Thread);

					this.board.totalThreads++;

					this.board.threadInfoProtector.release();

					this.board.moderatorEnabler.release();
                     
				}

				/*
				reap the moderator thread, close the server,
				
				
				kill threadPool (Careless Whispers BGM stops)
				*/

				mod_thread.wait(1000000);

			    this.threadPool.shutdown();
                    
				System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
				return;
			}
			catch (InterruptedException ex){
				System.err.println("An InterruptedException was caught: " + ex.getMessage());
				ex.printStackTrace();
				return;
			}
			catch (IOException i){
				return;
			}
			
		}

		
	}

	public static void main(String[] args) {
		for (int i=0; i<args.length; i++){
			int port = Integer.parseInt(args[i]);
			Thread tau = new Thread(new ScotlandYard(port));
			tau.start();
		}
	}
}