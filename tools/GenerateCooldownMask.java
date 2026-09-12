import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Mathematical coverage mask, not spell artwork. The native CircularProgressBar supplies the sweep. */
class GenerateCooldownMask {
    public static void main(String[] args) throws Exception {
        Path target=Path.of(args[0]);Files.createDirectories(target.getParent());
        var image=new BufferedImage(58,58,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<58;y++)for(int x=0;x<58;x++){
            // Fill the inset native icon face, clipping only its four chamfered corners.
            int edgeX=Math.min(x,57-x),edgeY=Math.min(y,57-y);
            image.setRGB(x,y,edgeX+edgeY>=5?0xffffffff:0x00ffffff);
        }
        if(!ImageIO.write(image,"png",target.toFile()))throw new IllegalStateException("PNG writer unavailable");
    }
}
