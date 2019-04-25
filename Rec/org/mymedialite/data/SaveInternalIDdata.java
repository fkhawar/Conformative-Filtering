package org.mymedialite.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Calendar;

import org.mymedialite.io.ItemData;
import org.mymedialite.io.ItemDataFileFormat;
import org.mymedialite.util.Utils;

/**
 * Read the data into IPosOnlyFeedback and save the dataset in the internal ID format.
 * The internal IDs should be the same everytime the "same" training-data-file is read 
 * @author fkhawar
 *
 */
public class SaveInternalIDdata {
	  static String training_file;
	  //static String test_file;
	  static IPosOnlyFeedback training_data;
	  static String data_dir = "";
	  static ItemDataFileFormat file_format = ItemDataFileFormat.DEFAULT;
	  static String libRecOrOcularPython="python";
	  
	  // ID mapping objects
	  static IEntityMapping user_mapping      = new EntityMapping();
	  static IEntityMapping item_mapping      = new EntityMapping();
	  
	  public static void main(String[] args) throws Exception {
		  
		  // Reading the arguments
		  for(String arg : args) {
		      int div = arg.indexOf("=") + 1;
		      String name;
		      String value;
		      if(div > 0) { 
		        name = arg.substring(0, div);
		        value = arg.substring(div);
		      } else {
		        name = arg;
		        value = null;
		      }

	      if(name.equals("--training-file="))             training_file        = value;
	      //else if(name.equals("--test-file="))            test_file            = value;
	      else if(name.equals("--data-dir="))             data_dir             = value;
	      
	      // Enum options
	      else if(name.equals("--file-format="))          file_format = ItemDataFileFormat.valueOf(value);
	      
	      else if(name.equals("--output-format="))		  libRecOrOcularPython = value; // either python or libRec
		  }// Reading arguments ended
		  
		  loadData();
		  writeData();
		  
	  }
	  
	  static void loadData() throws Exception {
		    long start = Calendar.getInstance().getTimeInMillis(); 
		    // training data
		    training_file = Utils.combine(data_dir, training_file);
		    training_data = ItemData.read(training_file, user_mapping, item_mapping, file_format == ItemDataFileFormat.IGNORE_FIRST_LINE);
		    
	  }
	  static void writeData() throws FileNotFoundException{
		  PrintWriter out = null;
		  out = new PrintWriter(training_file +"_InterIDdata");
		  if(libRecOrOcularPython.equals("python")) {
			  out = new PrintWriter(training_file +"_InterIDdata");
			  for (int i = 0; i < training_data.size(); i++){ // go over each index of dataset and print the user,item id
				  out.println(((DataSet)training_data).users.getInt(i)+";"+((DataSet)training_data).items.getInt(i));
				  
			  }
		  }
		  else if (libRecOrOcularPython.equals("libRec")) {
			  out = new PrintWriter(training_file +"_InterIDdataLibRec");
			  for (int i = 0; i < training_data.size(); i++){ // go over each index of dataset and print the user,item id
				  out.println(((DataSet)training_data).users.getInt(i)+","+((DataSet)training_data).items.getInt(i)+",1");
				  
			  }
		  }
		  
		  
		  out.close();
	  }
		  
}
