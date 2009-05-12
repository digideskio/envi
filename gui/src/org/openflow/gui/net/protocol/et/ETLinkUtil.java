package org.openflow.gui.net.protocol.et;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.openflow.gui.net.protocol.Link;
import org.openflow.gui.net.protocol.LinkType;
import org.openflow.gui.net.protocol.Node;

/**
 * Structure to specify a link utilization.
 * 
 * @author David Underhill
 */
public class ETLinkUtil extends Link {
    public static final int SIZEOF = Link.SIZEOF + 4;

    /** utilization of the link */
    public final float util;
    
    public ETLinkUtil(DataInput in) throws IOException {
        super(in); 
        this.util = in.readFloat();
    }
    
    public ETLinkUtil(LinkType linkType, Node srcNode, short srcPort, Node dstNode, short dstPort, float util) {
        super(linkType, srcNode, srcPort, dstNode, dstPort);
        this.util = util;
    }
    
    public void write(DataOutput out) throws IOException {
        super.write(out);
        out.writeFloat(util);
    }
    
    public String toString() {
        return super.toString() + ":util=" + util;
    }
}