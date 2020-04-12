package firstReboundGame;

import java.awt.Color;

public class ColorArrangement {
	public static ColorArrangement NULL_COLOURS = new ColorArrangement(null, null);
	private Color strokeColor;
	private Color fillColor;
	private Color struckStrokeColor;
	private Color struckFillColor;
	private Color secondaryFillColor;
	private Color struckSecondaryFillColor;
	
	public ColorArrangement(Color strokeColor, Color fillColor) {
		this.fillColor = fillColor;
		this.strokeColor = strokeColor;
	} // end of constructor
	
	public ColorArrangement(Color strokeColor, Color fillColor, Color struckStrokeColor, Color struckFillColor) {
		this.fillColor = fillColor;
		this.strokeColor = strokeColor;
		this.struckFillColor = struckFillColor;
		this.struckStrokeColor = struckStrokeColor;
	} // end of constructor
	
	public ColorArrangement(Color strokeColor, Color fillColor, Color struckStrokeColor, Color struckFillColor, Color secondaryFillColor, Color struckSecondaryFillColor) {
		this.fillColor = fillColor;
		this.strokeColor = strokeColor;
		this.struckFillColor = struckFillColor;
		this.struckStrokeColor = struckStrokeColor;
		this.secondaryFillColor = secondaryFillColor;
		this.struckSecondaryFillColor = struckSecondaryFillColor;
	} // end of constructor
	
	public Color getFillColor() { return fillColor; }
	public Color getStrokeColor() { return strokeColor; }
	public Color getStruckFillColor() { return struckFillColor; }
	public Color getStruckStrokeColor() { return struckStrokeColor; }
	public Color getSecondaryFillColor() { return secondaryFillColor; }
	public Color getStruckSecondaryFillColor() { return struckSecondaryFillColor; }
}

