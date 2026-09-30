package edu.ncsu.csc411.ps03.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class InputManager {

	public InputManager() {}
	
	public static int[][] loadMap(String filename) {
		ArrayList<int[]> map = new ArrayList<int[]>();
		try {
			File fi = new File(filename);
			Scanner fiReader = new Scanner(fi);
			while (fiReader.hasNextLine()) {
				String line = fiReader.nextLine();
				String[] lineArray = line.split(" ");
				int[] numbers = new int[lineArray.length];
				for(int i = 0; i < lineArray.length; i++) {
					String val = lineArray[i];
					numbers[i] = Integer.parseInt(val);
				}
				map.add(numbers);
			}
			fiReader.close();
		} catch (FileNotFoundException fnf) {
			String errorMsg = "%s is not a valid filename\n";
			System.out.printf(errorMsg, filename);
			fnf.printStackTrace();
		}
		
		return map.toArray(new int[0][0]);
	}

}
