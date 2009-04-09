package org.openflow.gui.drawables;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.gui.net.BackendConnection;
import org.openflow.gui.net.protocol.PollStart;
import org.openflow.gui.net.protocol.PollStop;
import org.openflow.gui.stats.LinkStats;
import org.openflow.protocol.AggregateStatsReply;
import org.openflow.protocol.AggregateStatsRequest;
import org.openflow.protocol.Match;
import org.pzgui.Constants;
import org.pzgui.AbstractDrawable;
import org.pzgui.StringDrawer;
import org.pzgui.icon.GeometricIcon;
import org.pzgui.layout.Edge;
import org.pzgui.math.IntersectionFinder;
import org.pzgui.math.Line;
import org.pzgui.math.Vector2f;

/**
 * Information about a link.
 * 
 * @author David Underhill
 */
public class Link extends AbstractDrawable implements Edge<NodeWithPorts> {
    /**
     * This exception is thrown if a link which already exists is tried to be 
     * re-created.
     */
    public static class LinkExistsException extends Exception {
        /** default constructor */
        public LinkExistsException() {
            super();
        }
        
        /** set the message associated with the exception */
        public LinkExistsException(String msg) {
            super(msg);
        }
    }
    
    /**
     * Constructs a new link between src and dst.
     * 
     * @param src  The source of data on this link.
     * @param dst  The endpoint of this link.
     * 
     * @throws LinkExistsException  thrown if the link already exists
     */
    public Link(NodeWithPorts dst, short dstPort, NodeWithPorts src, short srcPort) throws LinkExistsException {
        // do not re-create existing links
        if(src.getDirectedLinkTo(srcPort, dst, dstPort, true) != null)
            throw new LinkExistsException("Link construction error: link already exists");
        
        this.src = src;
        this.dst = dst;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        
        src.addLink(this);
        dst.addLink(this);
    }
    
    
    // --------- Basic Accessors / Mutators --------- //
    
    /** the source of this link */
    protected NodeWithPorts src;
    
    /** the port to which this link connects on the source node */
    protected short srcPort;
    
    /** the destination of this link */
    protected NodeWithPorts dst;
    
    /** the port to which this link connects on the destination node */
    protected short dstPort;
    
    /** whether this link is wired (else it is wireless) */
    private boolean wired = true;

    /** maximum capacity of the link */
    private double maxDataRate_bps = 1 * 1000 * 1000 * 1000; 
    
    /** whether the link is off because it "failed" */
    private boolean failed = false;
    
    /** 
     * Disconnects this link from its attached ports and stops tracking all 
     * statistics associated with this link.  stopTrackingAllStats() is called
     * by this method.   
     */
    public void disconnect(BackendConnection conn) throws IOException {
        src.getLinks().remove(this);
        dst.getLinks().remove(this);
        
        stopTrackingAllStats(conn);
    }
    
    /** get the souce of this link */
    public NodeWithPorts getSource() {
        return src;
    }
    
    /** get the destination of this link */
    public NodeWithPorts getDestination() {
        return dst;
    }
    
    /** 
     * Given one endpoint of the link, return the other endpoint.  Throws an 
     * error if p is neither the source or destination of this link.
     */
    public NodeWithPorts getOther(NodeWithPorts p) {
        // throw an error if n is neither src nor dst
        if(src!=p && dst!=p)
            throw new Error("Link::getOther Error: neither src (" + src
                    + ") nor dst (" + dst + ") is p (" + p + ")");
        
        return dst==p ? src : dst;
    }
    
    /** Gets the port number associated with the specified endpoint. */
    public short getMyPort(NodeWithPorts p) {
        if(src == p)
            return srcPort;
        else
            return dstPort;
    }
    
    /** Gets the port number associated with the endpoint which is not p. */
    public short getOtherPort(NodeWithPorts p) {
        if(src == p)
            return dstPort;
        else
            return srcPort;
    }
    
    /** returns true if the link is a wired link */
    public boolean isWired() {
        return wired;
    }
    
    /** returns true if the link is a wireless link */
    public boolean isWireless() {
        return !wired;
    }
    
    /** sets whether the link is a wired link */
    public void setWired(boolean wired) {
        this.wired = wired;
    }

    /** returns the maximum bandwidth which can be sent through the link in bps */
    public double getMaximumDataRate() {
        return maxDataRate_bps;
    }
    
    /** sets the maximum bandwidth which can be sent through the link in bps */
    public void setMaximumDataRate(double bps) {
        this.maxDataRate_bps = bps;
    }
    
    /** Returns true if the link has failed. */
    public boolean isFailed() {
        return failed;
    }
    
    /** Sets whether the link has failed. */
    public void setFailed(boolean b) {
        failed = b;
    }

    
    // ------------------- Drawing ------------------ //
    
    /** thickness of a link */
    public static final int LINE_WIDTH = 2;
    
    /** how the draw a link */
    public static final BasicStroke LINE_DEFAULT_STROKE = new BasicStroke(LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    
    /** whether to draw port numbers each link is attached to */
    public static boolean DRAW_PORT_NUMBERS = false;
    
    /** alpha channel of port numbers */
    public static double DEFAULT_PORT_NUM_ALPHA = 0.9;
    
    /** thickness of a tunnel */
    private static final int DEFAULT_TUNNEL_WIDTH = LINE_WIDTH * 10;
    
    /** gap between a tunnel and an endpoint */
    private static final int TUNNEL_DIST_FROM_BOX = DEFAULT_TUNNEL_WIDTH * 2;
    
    /** dark tunnel color*/
    public static final Color TUNNEL_PAINT_DARK = new Color(180, 180, 180);
    
    /** light tunnel color */
    public static final Color TUNNEL_PAINT_LIGHT = new Color(196, 196, 196);
    
    /** the color to draw the link (if null, then this link will not be drawn) */
    private Color curDrawColor = Color.BLACK;
    
    /** how much to offset the link drawing in the x axis */
    private int offsetX;
    
    /** how much to offset the link drawing in the y axis */
    private int offsetY;
    
    /** Bounds the area in which a link is drawn. */
    private Polygon boundingBox = null;
    
    /** Draws the link */
    public void drawObject(Graphics2D gfx) {
        // draw nothing if there is no current draw color for the link
        if(curDrawColor == null)
            return;
        
        Stroke s = gfx.getStroke();
        
        // outline the link if it is being hovered over or is selected
        if(isHovered())
            drawOutline(gfx, Constants.COLOR_HOVERING, 1.25);
        else if(isSelected())
            drawOutline(gfx, Constants.COLOR_SELECTED, 1.25);
        
        // draw the simple link as a line
        if(isWired())
            drawWiredLink(gfx);
        else
            drawWirelessLink(gfx);
        
        // draw the failure indicator if the link has failed
        if(isFailed())
            drawFailed(gfx);
        
        // draw the port numbers
        if(DRAW_PORT_NUMBERS)
            drawPortNumbers(gfx, DEFAULT_PORT_NUM_ALPHA);
        
        // restore the defaults
        gfx.setStroke(s);
        gfx.setPaint(Constants.PAINT_DEFAULT);
    }
    
    /** draw an "X" over the node to indicate failure */
    protected void drawFailed(Graphics2D gfx) {
        GeometricIcon.X.draw(gfx, 
                             (src.getX() + dst.getX())/2 + offsetX, 
                             (src.getY() + dst.getY())/2 + offsetY);
    }
    
    /**
     * Draw an outline around the link.
     * 
     * @param gfx           where to draw
     * @param outlineColor  color of the outline
     * @param ratio         how big to make the outline (relative to the 
     *                      bounding box of this link)
     */
    public void drawOutline(Graphics2D gfx, Paint outlineColor, double ratio) {
        AffineTransform af = new AffineTransform();
        af.setToScale(ratio, ratio);
        Shape s = af.createTransformedShape(boundingBox);
        
        gfx.draw(s);
        gfx.setPaint(outlineColor);
        gfx.fill(s);
    }
    
    /** draws port numbers by the link drawing's endpoints with the specified alpha */
    public void drawPortNumbers(Graphics2D gfx, double alpha) {
        gfx.setPaint(Constants.cmap(Color.RED));
        int srcPortX = (int)(alpha*src.getX() + (1.0-alpha)*dst.getX() + offsetX);
        int srcPortY = (int)(alpha*src.getY() + (1.0-alpha)*dst.getY() + offsetY);
        gfx.drawString(Short.toString(this.srcPort), srcPortX, srcPortY);
        
        gfx.setPaint(Constants.cmap(Color.GREEN.darker()));
        int dstPortX = (int)(alpha*dst.getX() + (1.0-alpha)*src.getX() + offsetX);
        int dstPortY = (int)(alpha*dst.getY() + (1.0-alpha)*src.getY() + offsetY);
        gfx.drawString(Short.toString(this.dstPort), dstPortX, dstPortY);
    }
    
    /** sets up the stroke and color information for the link prior to it being drawn */
    private void drawLinkPreparation(Graphics2D gfx) {
        gfx.setStroke(LINE_DEFAULT_STROKE);
        if(curDrawColor != null)
            gfx.setPaint(curDrawColor);
    }
    
    /** draws the link as a wired link between endpoints */
    public void drawWiredLink(Graphics2D gfx) {
        drawLinkPreparation(gfx);
        gfx.drawLine(src.getX() + offsetX, src.getY() + offsetY, 
                     dst.getX() + offsetX, dst.getY() + offsetY);
    }
    
    /** draws the link as a wireless link between endpoints */
    public void drawWirelessLink(Graphics2D gfx) {
        drawLinkPreparation(gfx);
        
        double m = (dst.getY() - src.getY()) / (double)(dst.getX() - src.getX());
        double d = 10;
        double dy = Math.sqrt((d * d) / (m * m + 1));
        if( dst.getY() < src.getY() ) dy = -dy;
        double dx = m * dy;
        boolean greater = src.getX() < dst.getX();
        
        int offset = 10;
        double x = src.getX() - offset;
        double y = src.getY();
        if( Math.abs(dx) > Math.abs(dy) ) {
            if( Math.abs(m) > 1.0 ) {
                double t = dy;
                dy = dx;
                dx = t;
            }
        }
        else if( Math.abs(dx) < Math.abs(dy) ) {
            if( Math.abs(m) < 1.0 ) {
                double t = dy;
                dy = dx;
                dx = t;
            }
        }
        boolean right = false;
        if( greater && dx<0 )  { dx = -dx; dy = -dy; right = true; }
        if( !greater && dx>0 ) { dx = -dx; dy = -dy; right = true; }
        
        while( (greater && (x+offset) < dst.getX()) || (!greater && (x+offset) > dst.getX()) ) {
            x += dx;
            y += dy;
            gfx.drawArc((int)x, (int)y, (int)30, (int)10, right?180:270, 90);
        }
    }
    
    /** draws a tunnel in the area used by the middle of the link */
    public void drawTunnel(Graphics2D gfx, int linkWidth) {
        // find the endpoints of the link based on the intersection of the link with its surrounding box
        Line linkLine = new Line(src.getX(), src.getY(), dst.getX(), dst.getY());
        Vector2f i1 = IntersectionFinder.intersectBox(linkLine, src);
        Vector2f i2 = IntersectionFinder.intersectBox(linkLine, dst);
        
        if(i1 == null) {
            Dimension dimSrc = src.getIcon().getSize();
            i1 = new Vector2f(src.getX()-dimSrc.width/2, src.getY()-dimSrc.height/2);
        }
         
        if(i2 == null) {
            Dimension dimDst = dst.getIcon().getSize();
            i2 = new Vector2f(dst.getX()-dimDst.width/2, dst.getY()-dimDst.height/2);
        }

        // determine the endpoints of the pipe as a fixed distance from the node/box
        float x1, y1, x2, y2;
        float d = TUNNEL_DIST_FROM_BOX;
        {
            float sx = i1.x;
            float sy = i1.y;
            float dx = i2.x;
            float dy = i2.y;
            float denom = (float)Math.sqrt(Math.pow(dx-sx,2) + Math.pow(sy-dy,2));
            x1 = sx + (dx - sx) * d / denom;
            y1 = sy - (sy - dy) * d / denom;
        }
        {
            float sx = i2.x;
            float sy = i2.y;
            float dx = i1.x;
            float dy = i1.y;
            float denom = (float)Math.sqrt(Math.pow(dx-sx,2) + Math.pow(sy-dy,2));
            x2 = sx + (dx - sx) * d / denom;
            y2 = sy - (sy - dy) * d / denom;
        }
        
        // center the tunnel on the coords
        int tw = DEFAULT_TUNNEL_WIDTH;
        x1 -= tw / 2;
        y1 -= tw / 2;
        x2 -= tw / 2;
        y2 -= tw / 2;
        
        // determine where to orient the corners of the rectangular portion of the cylinder
        Vector2f unitV = Vector2f.makeUnit(new Vector2f(x2-x1, y2-y1)).multiply(-tw/2);
        float t = unitV.x; unitV.x = unitV.y; unitV.y = t;
        int[] xs = new int[]{(int)(x1-unitV.x), (int)(x1+unitV.x), (int)(x2+unitV.x), (int)(x2-unitV.x)};
        int[] ys = new int[]{(int)(y1+unitV.y), (int)(y1-unitV.y), (int)(y2-unitV.y), (int)(y2+unitV.y)};
        for(int i=0; i<4; i++) {
            xs[i] += tw / 2;
            ys[i] += tw / 2;
        }
        
        // create the shapes representing pieces of the pipe
        Ellipse2D.Double circle1 = new Ellipse2D.Double(x1, y1, tw, tw);
        Ellipse2D.Double circle2 = new Ellipse2D.Double(x2, y2, tw, tw);
        Polygon pipe = new Polygon(xs, ys, 4);
        
        // build the paint for the gradient of the pipe
        Paint pipePaint = new GradientPaint((int)x1, (int)y1, TUNNEL_PAINT_DARK, (int)x2, (int)y2, TUNNEL_PAINT_LIGHT );
        
        // draw the pipe endpoints
        gfx.setPaint(pipePaint);
        gfx.fill(circle1);
        gfx.setPaint(Constants.PAINT_DEFAULT);
        gfx.draw(circle1);
        
        // draw the body of the pipe
        gfx.setPaint(pipePaint);
        gfx.fill(pipe);
        gfx.setPaint( Constants.PAINT_DEFAULT );
        gfx.drawLine( xs[0], ys[0], xs[3], ys[3] ); // long sides
        gfx.drawLine( xs[1], ys[1], xs[2], ys[2] );
        gfx.drawLine( xs[0], ys[0], xs[1], ys[1] ); // short sides
        gfx.drawLine( xs[2], ys[2], xs[3], ys[3] );
        
        // draw the pipe endpoints
        gfx.setPaint(pipePaint);
        
        // cover up the inner half of the first circle so the pipe looks 3D
        gfx.setStroke(Constants.STROKE_THICK);
        gfx.drawLine( xs[0], ys[0], xs[1], ys[1] );
        gfx.setStroke(Constants.STROKE_DEFAULT);
        
        gfx.fill(circle2);
        gfx.setPaint( Constants.PAINT_DEFAULT );
        gfx.draw(circle2);
        
        // extend the link into and out of the pipe
        BasicStroke strokeOutline = new BasicStroke( linkWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER );
        gfx.setStroke( strokeOutline );
        gfx.drawLine( (int)i2.x, (int)i2.y, (int)x2 + tw / 2, (int)y2 + tw / 2 );
        gfx.setStroke( Constants.STROKE_DEFAULT );
    }

    /** sets how many other links between the same endpoints have already been drawn */
    void setOffset(int numOtherLinks) {
        int ocount = ((numOtherLinks + 1) / 2) * 2;
        if(ocount == numOtherLinks)
            ocount = -ocount;
        
        offsetX = (LINE_WIDTH+2) * ocount;
        offsetY = (LINE_WIDTH+2) * ocount;
        
        updateBoundingBox(src.getX()+offsetX, src.getY()+offsetY, 
                          dst.getX()+offsetX, dst.getY()+offsetY);
    }
    
    /** updates the bounding box for this link */
    private void updateBoundingBox(int x1, int y1, int x2, int y2) {
        Vector2f from = new Vector2f(x1, y1);
        Vector2f to = new Vector2f(x2, y2);
        Vector2f dir = Vector2f.subtract            (to, from);
        Vector2f unitDir = Vector2f.makeUnit(dir);
        Vector2f perp = new Vector2f(unitDir.y, -unitDir.x).multiply(LINE_WIDTH / 2.0f + 4.0f);

        // build the bounding box
        float o = LINE_WIDTH / 2.0f;
        int[] bx = new int[]{ (int)(x1 - perp.x + o), (int)(x1 + perp.x + o), (int)(x2 + perp.x + o), (int)(x2 - perp.x + o) };
        int[] by = new int[]{ (int)(y1 - perp.y + o), (int)(y1 + perp.y + o), (int)(y2 + perp.y + o), (int)(y2 - perp.y + o) };
        boundingBox = new Polygon(bx, by, bx.length);
    }
    

    // -------------------- Stats ------------------- //
    
    /** statistics being gathered for this link */
    private final ConcurrentHashMap<Match, LinkStatsInfo> stats = new ConcurrentHashMap<Match, LinkStatsInfo>();
    
    /** pairs a message transaction ID with the stats it is collecting */
    private class LinkStatsInfo {
        /** transaction ID which will be used to update these statistics */
        public final int xid;
        
        /** whether these statistics are being polled by the backend for us */
        public final boolean isPolling;
        
        /** the statistics on traffic from the source and destination switch over this link */
        public final LinkStats stats;
        
        public LinkStatsInfo(int xid, boolean isPolling, Match m) {
            this.xid = xid;
            this.isPolling = isPolling;
            this.stats = new LinkStats(m);
        }
    }
    
    /** Gets the LinkStats associated with the specified Match, if any */
    public LinkStats getStats(Match m) {
        LinkStatsInfo lsi = stats.get(m);
        if(lsi != null)
            return lsi.stats;
        else
            return null;
    }
    
    /** 
     * Tells the link to acquire the specified stats (once).
     * 
     * @param m                  what statistics to get
     * @param conn               connection to talk to the backend over
     * @throws IOException       thrown if the connection fails
     */
    public void trackStats(Match m, BackendConnection conn) throws IOException {
        trackStats(0, m, conn);
    }
    
    /** 
     * Tells the link to acquire the specified stats.
     * 
     * @param pollInterval_msec  how often to refresh the stats (0 = only once)
     * @param m                  what statistics to get
     * @param conn               connection to talk to the backend over
     * @throws IOException       thrown if the connection fails
     */
    public void trackStats(int pollInterval_msec, Match m, BackendConnection conn) throws IOException {
        short pollInterval = (short)(( pollInterval_msec % 100 == 0)
                                     ? pollInterval_msec / 100
                                     : pollInterval_msec / 100 + 1);
        
        // build and send the message to get the stats
        AggregateStatsRequest req = new AggregateStatsRequest(src.getID(), srcPort, m);
        boolean isPolling = (pollInterval != 0);
        if(isPolling)
            conn.sendMessage(new PollStart(pollInterval, req));
        else
            conn.sendMessage(req);
        
        trackStats(m, req.xid, isPolling);
    }
    
    /**
     * Tells the link to setup stats for specified Match but do not acquire them automatically.
     * @param m  the match to setup stats for
     */
    public LinkStats trackStats(Match m) {
        return trackStats(m, 0, false);
    }
    
    /**
     * Tells the link to setup stats for specified Match but do not acquire them automatically.
     * @param m  the match to setup stats for
     * @param xid  the xid of the request which is acquiring stats for m
     * @param isPolling  whether the stats are being polled with xid
     */
    public LinkStats trackStats(Match m, int xid, boolean isPolling) {
        // remember that we are interested in these stats
        LinkStatsInfo lsi = new LinkStatsInfo(xid, isPolling, m);
        stats.put(m, lsi);
        return lsi.stats;
    }
    
    /**
     * Tells the link to stop tracking stats for the specified Match m.  If m 
     * was being polled, then a message will be sent to the backend to terminate
     * the polling of the message.
     * 
     * @param m  the match to stop collecting statistics for
     * @param conn  the connection over which to tell the backend to stop polling
     * @throws IOException  thrown if the connection fails
     */
    public void stopTrackingStats(Match m, BackendConnection conn) throws IOException {
        LinkStatsInfo lsi = stats.remove(m);
        if(lsi != null && lsi.isPolling)
            conn.sendMessage(new PollStop(lsi.xid));
    }
    
    /**
     * Stop tracking and clear all statistics associated with this link.
     *  
     * @param conn  the connection to send POLL_STOP messages over
     * @throws IOException  thrown if the connection fails
     */
    public void stopTrackingAllStats(BackendConnection conn) throws IOException {
        for(LinkStatsInfo lsi : stats.values())
            if(lsi.isPolling)
                conn.sendMessage(new PollStop(lsi.xid));
        
        stats.clear();
    }
    
    /** update this links with the latest stats reply about this link */
    public void updateStats(Match m, AggregateStatsReply reply) {
        LinkStatsInfo lsi = stats.get(m);
        if(lsi == null)
            System.err.println(this.toString() + " received stats it is not tracking: " + m.toString());
        else {
            if(reply.dpid == src.getID())
                lsi.stats.statsSrc.update(reply);
            else if(lsi.stats.statsDst != null && reply.dpid == dst.getID())
                lsi.stats.statsDst.update(reply);
            
            // update the color whenever the (unfiltered) link utilization stats are updated
            if(m.wildcards.isWildcardAll())
                setColorBasedOnCurrentUtilization();
        }
    }
    
    /** 
     * Returns the current bandwidth being sent through the link in ps or a 
     * value <0 if those stats are not currently being tracked. 
     */
    public double getCurrentDataRate() {
        LinkStats ls = getStats(Match.MATCH_ALL);
        if(ls == null)
            return -1;
        else
            return ls.getCurrentAverageDataRate();            
    }
    
    /** 
     * Returns the current utilization of the link in the range [0, 1] or -1 if
     * stats are not currently being tracked for this.
     */
    public double getCurrentUtilization() {
        double rate = getCurrentDataRate();
        if(rate < 0)
            return -1;
        else
            return rate / maxDataRate_bps;
    }
    
    
    // ------------- Usage Color Helpers ------------ //
    
    /** sets the color this link will be drawn based on the current utilization */
    public void setColorBasedOnCurrentUtilization() {
        float usage = (float)getCurrentUtilization();
        this.curDrawColor = getUsageColor(usage);
    }
    
    /**
     * Gets the color associated with a particular usage value.
     */
    public static Color getUsageColor(float usage) {
        if(usage < 0) {
            return Color.BLUE; // indicates that we don't know the utilization
        }
        else if (usage > 1)
            usage = 1;
        
        return USAGE_COLORS[(int)(usage * (NUM_USAGE_COLORS-1))];
    }
    
    /**
     * Computes the color associated with a particular usage value.
     */
    private static Color computeUsageColor(float usage) {
        if(usage == 0.0f)
            return new Color(0.3f, 0.3f, 0.3f, 0.5f); // faded gray
        else {
            float mid = 1.5f / 3.0f;
            
            if(usage < mid) {
                // blend green + yellow
                float alpha = usage / mid;
                return new Color(1.0f*alpha+0.0f*(1.0f-alpha),
                                 1.0f*alpha+1.0f*(1.0f-alpha),
                                 0.0f);
            }
            else {
                // blend red + yellow
                float alpha = (usage - mid) / mid;
                return new Color(1.0f*alpha+1.0f*(1.0f-alpha),
                                 0.0f*alpha+1.0f*(1.0f-alpha),
                                 0.0f);
            }
        }
    }
    
    /** how finely to precompute usage colors (larger => more memory and more preceise) */
    public static final int NUM_USAGE_COLORS = 256;
    
    /** array of precomputed usage colors */
    public static final Color[] USAGE_COLORS;
    
    /** image containing the legend of usage colors from low to high utilization */
    public static final  BufferedImage USAGE_LEGEND;
    
    /** precompute usage colors for performance reasons */
    static {
        USAGE_COLORS = new Color[NUM_USAGE_COLORS];
        int legendHeight = 20;
        USAGE_LEGEND = new BufferedImage(NUM_USAGE_COLORS, legendHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D gfx = (Graphics2D)USAGE_LEGEND.getGraphics();
        for(int i=0; i<NUM_USAGE_COLORS; i++) {
            USAGE_COLORS[i] = computeUsageColor(i / (float)(NUM_USAGE_COLORS-1));
            gfx.setPaint(USAGE_COLORS[i]);
            gfx.drawLine(i, 0, i, legendHeight);
        }
        gfx.setPaint(Constants.PAINT_DEFAULT);
        
        // draw the explanation on top of the legend
        gfx.setFont(Constants.FONT_DEFAULT);
        gfx.setColor(Color.BLACK);
        int lw = USAGE_LEGEND.getWidth();
        int lh = USAGE_LEGEND.getHeight();
        int y = lh / 2 + 5;
        int margin_x = 5;
        gfx.drawString("0%", margin_x, y);
        StringDrawer.drawCenteredString("Link Utilization (%)", gfx, lw / 2, y);
        StringDrawer.drawRightAlignedString("100%", gfx, lw - margin_x, y);
        gfx.setColor(Constants.COLOR_DEFAULT);
    }
    
    
    // -------------------- Other ------------------- //
    
    public boolean contains(int x, int y) {
        if(boundingBox == null)
            return false;
        else
            return boundingBox.contains(x, y);
    }
    
    public int hashCode() {
        int hash = 7;
        
        hash += dst.hashCode();
        hash += 31 * dstPort;
        hash += 31 * src.hashCode();
        hash += 31 * srcPort;
        
        return hash;
    }
    
    public boolean equals(Object o) {
        if(this == o) return true;
        if((o == null) || (o.getClass() != this.getClass())) return false;
        Link l = (Link)o;
        return l.dst.getID() == dst.getID() &&
               l.dstPort == dstPort &&
               l.src.getID() == src.getID() &&
               l.srcPort == srcPort;
    }
    
    public String toString() {
        return src.toString() + " ===> " + dst.toString();
    }
}
