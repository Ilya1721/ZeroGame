package zero_game;
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
import java.awt.Graphics;

public class Field extends Application
{
	private static final int PORT = 4444;
	private static final String host = "localhost";
	private boolean is_your_turn;
	private boolean is_win;
	private Canvas field;
	private Scene scene;
	private GraphicsContext g_context;
	private VBox main_layout;
	private HBox buttons_layout;
	private Button clear_button;
	private byte[] image_byte;
	private Socket client;
	private DataOutputStream outcoming;
	private DataInputStream incoming;
	private Thread connection_thread;
	private int click_count = 0;
	private int line_count = 2;
	private int line_height = 300;
	private int line_width = 300;
	private int cell_count = (int)Math.pow(line_count + 1, 2);
	private final int field_w = (line_count + 1) * line_width;
	private final int field_h = (line_count + 1) * line_height - 100;
	private final int scene_w = (line_count + 1) * line_width;
	private final int scene_h = (line_count + 1) * line_height;
	private class Cell 
	{
		private double start_x;
		private double end_x;
		private double start_y;
		private double end_y;
		private int player;
	}
	private Cell[] cell;
	private int current_player = 0;
	private final int player_one = 0;
	private final int player_two = 1;
	private final int null_player = -1;
	
	public static void main(String args[]) 
	{
		launch(args);
	}
	
	public void start(Stage my_stage) 
	{
		create_empty_field(field_w, field_h);
		create_scene(scene_w, scene_h);
		init_cell(10, Color.BLACK);
		add_clear_button();
		open_connection();
		
		my_stage.setTitle("Zero Game");
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
					if(connection_thread.isAlive()) 
					{
						connection_thread.stop();
					}
					if(client != null) 
					{
						close_connection();
					}
				}
				catch(Exception exc) 
				{
					System.out.println("Error at setting closing event");
					System.out.println(exc.toString());
				}
			}
		});
	}
	
	private void create_empty_field(int w, int h) 
	{
		field = new Canvas(w, h);
		g_context = field.getGraphicsContext2D();
		field.setOnMouseClicked(new EventHandler<MouseEvent>() 
		{
			public void handle(MouseEvent event) 
			{
				handle_click(event.getX(), event.getY());
			}
		});
	}
	
	private void create_scene(int w, int h) 
	{
		main_layout = new VBox();
		main_layout.getChildren().add(field);
		scene = new Scene(main_layout, w, h);
	}
	
	private void handle_click(double x, double y) 
	{
		if(is_your_turn) 
		{
			g_context.setStroke(Color.RED);
			g_context.setFill(Color.RED);
			g_context.setLineWidth(15);
			for(int i = 0; i < cell_count; ++i) 
			{
				if(between(x, cell[i].start_x, cell[i].end_x) &&
						between(y, cell[i].start_y, cell[i].end_y) &&
						cell[i].player == null_player) 
				{
					cell[i].player = current_player;
					if(current_player == player_one) 
					{
						g_context.strokeOval(x - 50, y - 50, 120, 120);
					}
					else 
					{
						g_context.strokeLine(x - 50, y - 50, x + 50, y + 50);
						g_context.strokeLine(x + 50, y - 50, x - 50, y + 50);
					}
					break;
				}
			}
			is_win = check_win();
			is_your_turn = false;
			try 
			{
				System.out.println("Sending data to server");
				WritableImage writable_image = new WritableImage((int)field.getWidth(), (int)field.getHeight());
				field.snapshot(null, writable_image);
				RenderedImage image = SwingFXUtils.fromFXImage(writable_image, null);
				ByteArrayOutputStream byte_stream = new ByteArrayOutputStream();
				ImageIO.write(image, "png", byte_stream);
				image_byte = byte_stream.toByteArray();
				if(is_win) 
				{
					outcoming.writeBoolean(false);
				}
				else 
				{
					outcoming.writeBoolean(true);
				}
				outcoming.writeInt(image_byte.length);
				outcoming.write(image_byte);
				outcoming.flush();
			}
			catch(Exception exc) 
			{
				System.out.println("Error at sending data to server");
				System.out.println(exc.toString());
			}
		}
	}
	
	private void init_cell(int line_w, Color color) 
	{
		g_context.setStroke(color);
		g_context.setLineWidth(line_w);
		cell = new Cell[cell_count];
		for(int i = 0; i < cell_count; ++i) 
		{
			cell[i] = new Cell();
			cell[i].player = null_player;
		}
		for(int i = 0, x = 250, y = 200; i < cell_count; ++i) 
		{
			cell[i].start_x = x;
			cell[i].end_x = x + 150;
			cell[i].start_y = y;
			cell[i].end_y = y + 150;
			y += 150;
			if((i + 1) % 3 == 0 && i != 0) 
			{
				x += 150;
				y = 200;
			}
			g_context.strokeRect(cell[i].start_x, cell[i].start_y,
					cell[i].end_x - cell[i].start_x, cell[i].end_y - cell[i].start_y);
		}
	}
	
	private boolean between(double a, double b, double c) 
	{
		return a >= b && a <= c;
	}
	
	private boolean check_win() 
	{
		int player_on_cell = -1;
		int sqrt_cell_count = (int)Math.sqrt((double)cell_count);
		boolean is_win = false;
		int cells_to_win = 3;
		int player_one_count = 0;
		int player_two_count = 0;
		Paint prev_color;
		++click_count;
		for(int i = 0; i < sqrt_cell_count && is_win == false; ++i) 
		{
			player_on_cell = cell[i].player;
			for(int j = i, k = 0; k < sqrt_cell_count; j += 3, ++k) 
			{
				if(cell[j].player != null_player && cell[j].player == player_on_cell) 
				{
					if(player_on_cell == player_one) 
					{
						++player_one_count;
					}
					else 
					{
						++player_two_count;
					}
				}
				if(player_one_count == cells_to_win || player_two_count == cells_to_win) 
				{
					prev_color = g_context.getStroke();
					g_context.setStroke(Color.BLUE);
					g_context.strokeLine(cell[i].start_x, (cell[i].start_y + cell[i].end_y) / 2,
							cell[j].end_x, (cell[j].start_y + cell[j].end_y) / 2);
					is_win = true;
					g_context.setStroke(prev_color);
					return is_win;
				}
			}
			player_one_count = 0;
			player_two_count = 0;
		}
		for(int i = 0, k = 0; k < sqrt_cell_count && is_win == false; i += 3, ++k) 
		{
			player_on_cell = cell[i].player;
			for(int j = i; j < i + 3; ++j)
			{
				if(cell[j].player != null_player && cell[j].player == player_on_cell) 
				{
					if(player_on_cell == player_one) 
					{
						++player_one_count;
					}
					else 
					{
						++player_two_count;
					}
				}
				if(player_one_count == cells_to_win || player_two_count == cells_to_win) 
				{
					prev_color = g_context.getStroke();
					g_context.setStroke(Color.BLUE);
					g_context.strokeLine((cell[i].start_x + cell[i].end_x) / 2, cell[i].start_y,
							(cell[j].start_x + cell[j].end_x) / 2, cell[j].end_y);
					is_win = true;
					return is_win;
				}
			}
			player_one_count = 0;
			player_two_count = 0;
		}
		player_on_cell = cell[0].player;
		for(int i = 0, k = 0; k < sqrt_cell_count && is_win == false; i += 4, ++k) 
		{
			if(cell[i].player != null_player && cell[i].player == player_on_cell) 
			{
				if(player_on_cell == player_one) 
				{
					++player_one_count;
				}
				else 
				{
					++player_two_count;
				}
			}
			if(player_one_count == cells_to_win || player_two_count == cells_to_win) 
			{
				prev_color = g_context.getStroke();
				g_context.setStroke(Color.BLUE);
				g_context.strokeLine(cell[0].start_x, cell[0].start_y, cell[i].end_x, cell[i].end_y);
				is_win = true;
				return is_win;
			}
		}
		player_one_count = 0;
		player_two_count = 0;
		player_on_cell = cell[2].player;
		for(int i = 2, k = 0; k < sqrt_cell_count && is_win == false; i += 2, ++k) 
		{
			if(cell[i].player != null_player && cell[i].player == player_on_cell) 
			{
				if(player_on_cell == player_one) 
				{
					++player_one_count;
				}
				else 
				{
					++player_two_count;
				}
			}
			if(player_one_count == cells_to_win || player_two_count == cells_to_win) 
			{
				prev_color = g_context.getStroke();
				g_context.setStroke(Color.BLUE);
				g_context.strokeLine(cell[2].start_x, cell[2].start_y + 150, cell[i].end_x, cell[i].end_y - 150);
				is_win = true;
				return is_win;
			}
		}
		player_one_count = 0;
		player_two_count = 0;
		if(click_count == 9) 
		{
			is_win = true;
			click_count = 0;
		}
		return is_win;
	}
	
	private void add_clear_button() 
	{
		clear_button = new Button("Clear field");
		buttons_layout = new HBox();
		buttons_layout.getChildren().add(clear_button);
		buttons_layout.setAlignment(Pos.CENTER);
		main_layout.getChildren().add(buttons_layout);
		clear_button.setOnAction(new EventHandler<ActionEvent>() 
		{
			public void handle(ActionEvent event) 
			{
				try 
				{
					System.out.println("Sending signal to clear field");
					g_context.clearRect(0, 0, field_w, field_h);
					init_cell(10, Color.BLACK);
				}
				catch(Exception exc) 
				{
					System.out.println("Error at sending signal to clear field");
					System.out.println(exc.toString());
				}
			}
		});
	}
	
	private void open_connection() 
	{
		connection_thread = new Thread(new Runnable() 
		{
			public void run() 
			{
				try 
				{
					System.out.println("Connecting to server");
					client = new Socket(host, PORT);
				}
				catch(Exception exc) 
				{
					System.out.println("Error at opening connection with server");
					System.out.println(exc.toString());
				}
				System.out.println("Client connected");
				set_streams();
				hand_shake();
				while(true) 
				{
					get_data();
				}
			}
		});
		connection_thread.start();
	}
	
	private void set_streams() 
	{
		try 
		{
			System.out.println("Setting streams");
			outcoming = new DataOutputStream
					(new BufferedOutputStream(client.getOutputStream()));
			incoming = new DataInputStream
					(new BufferedInputStream(client.getInputStream()));
		}
		catch(Exception exc) 
		{
			System.out.println("Error at setting streams with server");
			System.out.println(exc.toString());
		}
	}
	
	private void hand_shake() 
	{
		System.out.println("Setting current player");
		try 
		{
			current_player = incoming.readInt();
			is_your_turn = incoming.readBoolean();
		}
		catch(Exception exc) 
		{
			System.out.println("Error at setting current player");
			System.out.println(exc.toString());
		}
	}
	
	private void get_data() 
	{
		try 
		{
			System.out.println("Getting data from server");
			is_your_turn = incoming.readBoolean();
			int array_lenght = incoming.readInt();
			image_byte = new byte[array_lenght];
			incoming.read(image_byte);
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(image_byte));
			WritableImage writable_image = SwingFXUtils.toFXImage(image, null);
			g_context.clearRect(0, 0, field_w, field_h);
			g_context.drawImage(writable_image, 0, 0);	
		}
		catch(Exception exc) 
		{
			System.out.println("Error at getting data from server");
			System.out.println(exc.toString());
			connection_thread.stop();
		}
	}
	
	private void close_streams() 
	{
		try 
		{
			System.out.println("Closing streams with server");
			if(client != null) 
			{
				outcoming.close();
				incoming.close();
			}
		}
		catch(Exception exc) 
		{
			System.out.println("Error at closing streams with server");
			System.out.println(exc.toString());
		}
	}
	
	private void close_connection() 
	{
		try 
		{
			if(client != null) 
			{
				System.out.println("Closing client");
				client.close();
			}
		}
		catch(Exception exc) 
		{
			System.out.println("Error at closing connection with server");
			System.out.println(exc.toString());
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}















