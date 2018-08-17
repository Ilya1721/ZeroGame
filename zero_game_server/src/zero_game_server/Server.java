package zero_game_server;
import javafx.application.*;
import javafx.scene.canvas.*;
import javafx.scene.paint.*;
import javafx.stage.*;
import javafx.scene.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.event.*;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import java.net.*;
import java.util.Scanner;
import java.io.*;
import javax.imageio.ImageIO;
import javafx.scene.image.*;
import java.awt.image.*;
import javafx.embed.swing.SwingFXUtils;

public class Server extends Application
{
	private static final int PORT = 4444;
	private final int width = 800;
	private final int height = 600;
	private final int client_count = 2;
	private final int player_one = 0;
	private final int player_two = 1;
	private final String host = "localhost";
	private ServerSocket server;
	private Socket client[];
	private DataOutputStream[] outcoming;
	private DataInputStream[] incoming;
	private boolean is_your_turn;
	private boolean[] signals_to_clear;
	private Scene scene;
	private VBox main_layout;
	private byte[] image_byte;
	private Thread connection_thread;
	
	public void start(Stage my_stage) 
	{
		init_ui();
		open_connection();
		
		my_stage.setTitle("Server");
		my_stage.setScene(scene);
		my_stage.show();
		my_stage.setOnCloseRequest(new EventHandler<WindowEvent>() 
		{
			public void handle(WindowEvent event) 
			{
				try 
				{
					System.out.println("Setting closing event");
					close_streams();
					if(server != null) 
					{
						server.close();
					}
					for(int i = 0; i < client_count; ++i) 
					{
						if(client[i] != null) 
						{
							client[i].close();
						}
					}
					if(connection_thread.isAlive()) 
					{
						connection_thread.stop();
					}
				}
				catch(Exception exc) 
				{
					System.out.println("Error at closing event");
					System.out.println(exc.toString());
				}
			} 
		});
	}
	
	public static void main(String args[]) 
	{
		launch(args);
	}
	
	private void init_ui() 
	{
		System.out.println("Setting ui");
		main_layout = new VBox();
		scene = new Scene(main_layout, width, height);
	}
	
	private void open_connection() 
	{
		connection_thread = new Thread(new Runnable() 
		{
			public void run() 
			{
				try 
				{
					System.out.println("Starting server");
					server = new ServerSocket(PORT);
					client = new Socket[client_count];
					for(int i = 0; i < client_count; ++i) 
					{
						System.out.println("Waiting for client to connect");
						client[i] = server.accept();
					}
				}
				catch(Exception exc) 
				{
					System.out.println("Error at opening connection at server side");
					System.out.println(exc.toString());
				}
				System.out.println("Clients connected");
				init_util();
				set_streams();
				hand_shake();
				while(true) 
				{
					get_data(player_one);
					send_data(player_two);
					get_data(player_two);
					send_data(player_one);
				}
			}
		});
		connection_thread.start();
	}
	
	private void init_util() 
	{
		signals_to_clear = new boolean[client_count];
	}
	
	private void set_streams() 
	{
		try 
		{
			System.out.println("Setting streams with clients");
			outcoming = new DataOutputStream[client_count];
			incoming = new DataInputStream[client_count];
			for(int i = 0; i < client_count; ++i) 
			{
				outcoming[i] = new DataOutputStream
						(new BufferedOutputStream(client[i].getOutputStream()));
				incoming[i] = new DataInputStream
						(new BufferedInputStream(client[i].getInputStream()));
			}
		}
		catch(Exception exc) 
		{
			System.out.println("Error at setting streams with clients");
			System.out.println(exc.toString());
		}
	}
	
	private void hand_shake() 
	{
		System.out.println("Setting players ids");
		try 
		{
			outcoming[player_one].writeInt(player_one);
			outcoming[player_one].writeBoolean(true);
			outcoming[player_one].flush();
			outcoming[player_two].writeInt(player_two);
			outcoming[player_two].writeBoolean(false);
			outcoming[player_two].flush();
		}
		catch(Exception exc) 
		{
			System.out.println("Error at setting players ids");
			System.out.println(exc.toString());
		}
	}
	
	private void close_streams() 
	{
		try 
		{
			System.out.println("Setting streams with clients");
			for(int i = 0; i < client_count; ++i) 
			{
				if(client[i] != null) 
				{
					incoming[i].close();
					outcoming[i].close();
				}
			}
		}
		catch(Exception exc) 
		{
			System.out.println("Error at closing streams with clients");
			System.out.println(exc.toString());
		}
	}
	
	private void get_data(int i) 
	{
		try 
		{
			System.out.println("Getting data from client " + i);
			is_your_turn = incoming[i].readBoolean();
			int array_lenght = incoming[i].readInt();
			image_byte = new byte[array_lenght];
			incoming[i].read(image_byte);
		}
		catch(Exception exc) 
		{
			System.out.println("Error at getting data from client " + i);
			System.out.println(exc.toString());
			connection_thread.stop();
		}
	}
	
	private void send_data(int i) 
	{
		try 
		{
			System.out.println("Sendind data to client " + i);
			outcoming[i].writeBoolean(is_your_turn);
			outcoming[i].writeInt(image_byte.length);
			outcoming[i].write(image_byte);
			outcoming[i].flush();
		}
		catch(Exception exc) 
		{
			System.out.println("Error at sending data to client " + i);
			System.out.println(exc.toString());
			connection_thread.stop();
		}
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
