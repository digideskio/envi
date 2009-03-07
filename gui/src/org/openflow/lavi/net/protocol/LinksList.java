package org.openflow.lavi.net.protocol;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A list of switches.
 * 
 * @author David Underhill
 */
public abstract class LinksList extends LAVIMessage {
    public Link[] links;
    
    public LinksList(LAVIMessageType t, final Link[] links) {
        this(t, 0, links);
    }
    
    public LinksList(LAVIMessageType t, int xid, final Link[] links) {
        super(t, xid);
        this.links = links;
    }
    
    public LinksList(final int len, final LAVIMessageType t, final int xid, final DataInput in) throws IOException {
        super(t, xid);
        
        // read the source DPID associated with all links in this missage
        long srcDPID = in.readLong();
        final int SIZEOF_REST_OF_LINK = Link.SIZEOF - 8; 
        
        // make sure the number of bytes leftover makes sense
        int left = len - super.length() - 8;
        if(left % SIZEOF_REST_OF_LINK != 0) {
            throw new IOException("Body of links list is not a multiple of " + (Link.SIZEOF-8) + " (length of body is " + left + " bytes)");
        }
        
        // read in the DPIDs
        int index = 0;
        links = new Link[left / SIZEOF_REST_OF_LINK];
        while(left >= SIZEOF_REST_OF_LINK) {
            left -= SIZEOF_REST_OF_LINK;
            links[index++] = new Link(srcDPID, in.readShort(), in.readLong(), in.readShort());
        }
    }
    
    public int length() {
        return super.length() + links.length * Link.SIZEOF;
    }
    
    public void write(DataOutput out) throws IOException {
        super.write(out);
        for(Link l : links)
            l.write(out);
    }
    
    public String toString() {
        String strLinks;
        if(links.length > 0)
            strLinks = links[0].toString();
        else
            strLinks = "";
        
        for(int i=1; i<links.length; i++)
            strLinks += ", " + links[i].toString();
        
        return super.toString() + TSSEP + strLinks;
    }
}