public class Rectangle {
  int x, y, width, height;
  public Rectangle(int x, int y, int width, int height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }
  
  public boolean contains(float x2, float y2) {
    int ix = (int) x2;
    int iy = (int) y2;
    return (ix >= x && ix <= x + width
         && iy >= y && iy <= y + height); 
  }
}
