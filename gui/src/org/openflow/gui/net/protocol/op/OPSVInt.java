/**
 * 
 */
package org.openflow.gui.net.protocol.op;

import java.io.DataInput;
import java.io.IOException;

/**
 * Integer value of a state variable
 * 
 * @author grg
 *
 */
public class OPSVInt extends OPStateValue {
    /** width of field */
    public final int width;
    
    /** value of field */
    public final long value;
    
    protected OPSVInt(String name, DataInput in) throws IOException {
        super(name);
        width = in.readByte();
        if (width <= 4)
            value = in.readInt();
        else
            value = in.readLong();
    }
    
    public int length() {
        return super.length() + 4 + ((width <= 4) ? 4 : 8);
    }

    public String toString() {
        return super.toString() + "type=int width=" + width + 
            " value=" + value + " (" + Long.toHexString(value) + ")"; 
    }	
}