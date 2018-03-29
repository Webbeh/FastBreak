package ch.geeq;

import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.Callable;

/**
 * @author weby@we-bb.com [Nicolas Glassey]
 * @version 1.0.0
 * @since 3/25/18
 */
public class GetBlock implements Callable<Block> {
    private BlockPosition l;
    private World w;
    GetBlock(BlockPosition l, World w)
    {
        this.l = l;
        this.w = w;
    }
    @Override
    public Block call() throws Exception {
        return w.getBlockAt(new Location(w, l.getX(), l.getY(), l.getZ()));
    }
}
