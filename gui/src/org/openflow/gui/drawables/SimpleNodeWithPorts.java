package org.openflow.gui.drawables;

import org.openflow.gui.net.protocol.NodeType;
import org.openflow.util.string.DPIDUtil;
import org.pzgui.icon.Icon;

/**
 * Describes a simple NodeWithPorts.
 * 
 * @author David Underhill
 */
public class SimpleNodeWithPorts extends NodeWithPorts {
    public SimpleNodeWithPorts(NodeType type, long id, Icon icon) {
        this(type, "", 0, 0, id, icon);
        
    }
    
    public SimpleNodeWithPorts(NodeType type, String name, int x, int y, long id, Icon icon) {
        super(name, x, y, icon);
        this.id = id;
        this.type = type;
    }
    
    /** type of this node */
    private NodeType type;
    
    /** ID of this node */
    private long id;
    
    /** returns a string version of the node's datapath ID */
    public String getDebugName() {
        return DPIDUtil.toString(getID());
    }
    
    /** gets the id of this node */
    public long getID() {
        return id;
    }
    
    /** gets the type of this node */
    public NodeType getType() {
        return type;
    }
     
    public String toString() {
        String body = type.toString() + "-" + DPIDUtil.toString(getID());
        
        String name = getName();
        if(name == null || name.length()==0)
            return body;
        else
            return name + "{" + body + "}";
    }
}
