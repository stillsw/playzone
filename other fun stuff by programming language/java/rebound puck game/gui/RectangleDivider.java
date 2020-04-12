package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;

/*
 * A simple layout area for dividing an area into a tabular layout
 * Layout is tabular (rows/columns)
 */
public class RectangleDivider
{
	private Rectangle enclosingRectangle;
	private int border;
	private int cellMargin;
	private double enclosedWidth;
	private double enclosedHeight;
	private int rows;
	private int columns;
	private double cellWidth;
	private double cellHeight;
	

	public RectangleDivider(Rectangle enclosingRectangle, int rows, int columns, int border, int cellMargin) {
		this.enclosingRectangle = enclosingRectangle;
		this.border = border;
		this.cellMargin = cellMargin;
		this.rows = rows;
		this.columns = columns;
		this.enclosedWidth = this.enclosingRectangle.width - this.border * 2;
		this.enclosedHeight = this.enclosingRectangle.height - this.border * 2;
		this.cellWidth = this.enclosedWidth / columns;
		this.cellHeight = this.enclosedHeight / rows;
		
// something like this if want to create rectangle[][]		
//		private Rectangle[][] cells;

//		this.cells = new Rectangle[rows][];	
//		for (int r = 0; r < rows; r++) {
//			int ypos = this.border + r * cellHeight;
//			Rectangle[] cols = new Rectangle[columns];
//			this.cells[r] = cols;
//			for (int c = 0; c < columns; c++) {
//				int xpos = this.border + c * cellWidth;
//				cols[c] = new Rectangle(xpos, ypos, cellWidth, cellHeight);
//			}
//		}
		
	}
	
	public Rectangle getEnclosingRectangle() { return this.enclosingRectangle; }
	public int getBorder() { return this.border; }
	public int getCellMargin() { return this.cellMargin; }
	public double getEnclosedWidth() { return this.enclosedWidth; }
	public double getEnclosedHeight() { return this.enclosedHeight; }
	public int getRows() { return this.rows; }
	public int getColumns() { return this.columns; }
	public double getCellWidth() { return this.cellWidth; }
	public double getCellHeight() { return this.cellHeight; }
	
	public Rectangle2D getCellRectangle2D(int row, int column) {
		return this.getCellRectangle2D(row, column, 1, 1);
	}
	
	public Rectangle2D getCellRectangle2D(int row, int column, int numRows, int numColumns) {
		if (row + numRows > this.rows || column + numColumns > this.columns)
			System.out.println("RectangleDivider.getCellRectangle2D: warning request for cell exceeds bounds (row+numRows="+(row + numRows)+" column+numColumns="+(column + numColumns)+")");
		double xpos = this.border + column * this.cellWidth + this.cellMargin;
		double ypos = this.border + row * this.cellHeight + this.cellMargin;
		return new Rectangle2D.Double(xpos, ypos, this.cellWidth * numColumns - this.cellMargin * 2, this.cellHeight * numRows - this.cellMargin * 2);
		
	}
	
}