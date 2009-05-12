"""Defines the OpenFlow GUI-based Elastic Tree protocol."""

import struct

from twisted.internet import reactor

from OFGMessage import dpidstr, OFGMessage, OFG_MESSAGES, Link, NodesList, run_ofg_server
from ltprotocol.ltprotocol import LTProtocol

ET_MESSAGES = []

class ETTrafficMatrix(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF0

    SOLVER_SPREAD = 1
    SOLVER_SQUISH = 2
    SOLVER_MODEL_GLPK = 3
    SOLVER_MODEL_GAMS = 4
    SOLVER_HASH = 5

    def __init__(self, use_hw, solver, may_split_flows, k, demand, edge, agg, plen, xid=0):
        OFGMessage.__init__(self, xid)
        self.use_hw = bool(use_hw)
        self.solver = int(solver)
        self.may_split_flows = bool(may_split_flows)
        self.k      = int(k)
        self.demand = float(demand)
        self.edge   = float(edge)
        self.agg    = float(agg)
        self.plen   = int(plen)
        if self.demand<0.0 or self.demand>1.0 or self.edge<0.0 or self.edge>1.0 or self.agg<0.0 or self.edge>1.0:
            raise Exception("demand (%f), edge (%f) and agg (%f) must be between 0.0 and 1.0 inclusive" % (self.demand, self.edge, self.agg))
        if self.agg + self.edge > 1.0:
            if self.agg + self.edge > 1.01: # more than floating point imprecision
                raise Exception("agg + edge > 1.0 (%f)" % (self.agg+self.edge))
            else:
                self.agg = 1.0 - self.edge

    def length(self):
        return OFGMessage.SIZE + 23

    def pack(self):
        return OFGMessage.pack(self) + struct.pack('> 3B I 3f I',
                                                   self.use_hw, self.solver, self.may_split_flows,
                                                   self.k, self.demand, self.edge, self.agg, self.plen)

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        t = struct.unpack('> 3B I 3f I', body[:23])
        return ETTrafficMatrix(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7], xid)

    def __str__(self):
        fmt = 'ET_TRAFFIC_MATRIX: ' + OFGMessage.__str__(self) + " hw=%u alg=%u split=%u k=%u demand=%u edge=%u agg=%u plen=%u"
        return fmt % (self.use_hw, self.solver, self.may_split_flows,
                      self.k, self.demand, self.edge, self.agg, self.plen)
ET_MESSAGES.append(ETTrafficMatrix)

class ETLinkUtil(Link):
    SIZE = Link.SIZE + 4

    def __init__(self, link_type, src_node, src_port, dst_node, dst_port, util):
        Link.__init__(self, link_type, src_node, src_port, dst_node, dst_port)
        self.util = float(util)

    def pack(self):
        return Link.pack(self) + struct.pack('> f', self.util)

    @staticmethod
    def unpack(buf):
        link_type = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        src_node = Node.unpack(buf[:Node.SIZE])
        buf = buf[Node.SIZE:]
        src_port = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        dst_node = Node.unpack(buf[:Node.SIZE])
        buf = buf[Node.SIZE:]
        dst_port = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        util = struct.unpack('> f', buf[:4])[0]
        return ETLinkUtil(link_type, src_node, src_port, dst_node, dst_port, util)

class ETLinkUtils(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF1

    def __init__(self, utils, xid=0):
        OFGMessage.__init__(self, xid)
        self.utils = utils

    def length(self):
        return OFGMessage.SIZE + self.utils * ETLinkUtil.SIZE

    def pack(self):
        hdr = OFGMessage.pack(self)
        return hdr + ''.join([util.pack() for util in self.utils])

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        num_utils = len(body) / ETLinkUtil.SIZE
        utils = []
        for _ in range(num_utils):
            utils.append(Link.unpack(body[:ETLinkUtil.SIZE]))
            body = body[ETLinkUtil.SIZE:]
        return ETLinkUtils(utils, xid)

    def __str__(self):
        return 'ET_LINK_UTILS: ' + OFGMessage.__str__(self) + ' utils=%s' % str(self.utils)
ET_MESSAGES.append(ETLinkUtils)

class ETPowerUsage(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF2

    def __init__(self, watts_current, watts_traditional, watts_max=None, xid=0):
        OFGMessage.__init__(self, xid)
        self.watts_current = int(watts_current)
        self.watts_traditional = int(watts_traditional)
        self.watts_max = int(watts_max if watts_max is not None else watts_traditional)

    def length(self):
        return OFGMessage.SIZE + 12

    def pack(self):
        body = struct.pack('> 3I', self.watts_current, self.watts_traditional, self.watts_max)
        return OFGMessage.pack(self) + body

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        watts_current = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        watts_traditional = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        watts_max = struct.unpack('> I', body[:4])[0]
        return ETPowerUsage(watts_current, watts_traditional, watts_max, xid)

    def __str__(self):
        fmt = 'ET_POWER_USAGE: ' + OFGMessage.__str__(self) + " cur=%u trad=%u max=%u"
        return fmt % (self.watts_current, self.watts_traditional, self.watts_max)
ET_MESSAGES.append(ETPowerUsage)

class ETSwitchesOff(NodesList):
    @staticmethod
    def get_type():
        return 0xF3

    def __init__(self, nodes, xid=0):
        NodesList.__init__(self, nodes, xid)

    def __str__(self):
        return 'NODES_OFF: ' + NodesList.__str__(self)
ET_MESSAGES.append(ETSwitchesOff)

class ETBandwidth(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF4

    def __init__(self, bandwidth_achieved_mbps, xid=0):
        OFGMessage.__init__(self, xid)
        self.bandwidth_achieved_mbps = int(bandwidth_achieved_mbps)

    def length(self):
        return OFGMessage.SIZE + 4

    def pack(self):
        body = struct.pack('> I', self.bandwidth_achieved_mbps)
        return OFGMessage.pack(self) + body

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        bandwidth_achieved_mbps = struct.unpack('> I', body[:4])[0]
        return ETPowerUsage(bandwidth_achieved_mbps, xid)

    def __str__(self):
        fmt = 'ET_BANDWIDTH_ACHIEVED: ' + OFGMessage.__str__(self) + " %u Mbps"
        return fmt % self.bandwidth_achieved_mbps
ET_MESSAGES.append(ETBandwidth)

class ETLatency(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF5

    def __init__(self, latency_ms_edge, latency_ms_agg, latency_ms_core, xid=0):
        OFGMessage.__init__(self, xid)
        self.latency_ms_edge = int(latency_ms_edge)
        self.latency_ms_agg = int(latency_ms_agg)
        self.latency_ms_core = int(latency_ms_core)

    def length(self):
        return OFGMessage.SIZE + 12

    def pack(self):
        body = struct.pack('> 3I', self.latency_ms_edge, self.latency_ms_agg, self.latency_ms_core)
        return OFGMessage.pack(self) + body

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        latency_ms_edge = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        latency_ms_agg = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        latency_ms_core = struct.unpack('> I', body[:4])[0]
        return ETLatency(latency_ms_edge, latency_ms_agg, latency_ms_core, xid)

    def __str__(self):
        fmt = 'ET_LATENCY: ' + OFGMessage.__str__(self) + " edge_ms=%u agg_ms=%u core_ms=%u"
        return fmt % (self.latency_ms_edge, self.latency_ms_agg, self.latency_ms_core)
ET_MESSAGES.append(ETLatency)

class ETSwitchesRequest(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF6

    def __init__(self, k, xid=0):
        OFGMessage.__init__(self, xid)
        self.k = int(k)

    def length(self):
        return OFGMessage.SIZE + 4

    def pack(self):
        return OFGMessage.pack(self) + struct.pack('> I', self.k)

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        k = struct.unpack('> I', body[:4])[0]
        return ETSwitchesRequest(k, xid)

    def __str__(self):
        return 'ET_SWITCHES_REQUEST: ' + OFGMessage.__str__(self) + ' k=%u' % self.k
ET_MESSAGES.append(ETSwitchesRequest)

class ETSwitchFailureChange(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF7

    def __init__(self, dpid, failed, xid=0):
        OFGMessage.__init__(self, xid)
        self.dpid = long(dpid)
        self.failed = failed

    def length(self):
        return OFGMessage.SIZE + 8 + 1

    def pack(self):
        return OFGMessage.pack(self) + struct.pack('> QB', self.dpid, (1 if self.failed else 0))

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        dpid = struct.unpack('> Q', body[:8])[0]
        body = body[8:]
        failed = struct.unpack('> B', body[:1])[0]
        return ETSwitchFailureChange(dpid, True if failed==1 else False, xid)

    def __str__(self):
        what = ' ' if self.failed else ' un'
        return 'ET_SWITCH_FAILURE_CHANGE: ' + OFGMessage.__str__(self) + dpidstr(self.dpid) + ' => ' + what + 'fail'
ET_MESSAGES.append(ETSwitchFailureChange)

class ETLinkFailureChange(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF8

    def __init__(self, link, failed, xid=0):
        OFGMessage.__init__(self, xid)
        self.link = link
        self.failed = failed

    def length(self):
        return OFGMessage.SIZE + Link.SIZE + 1

    def pack(self):
        return OFGMessage.pack(self) + self.link.pack() + struct.pack('> B', (1 if self.failed else 0))

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        link = Link.unpack(body[:Link.SIZE])
        body = body[Link.SIZE:]
        failed = struct.unpack('> B', body[:1])[0]
        return ETLinkFailureChange(link, True if failed==1 else False, xid)

    def __str__(self):
        what = ' ' if self.failed else ' un'
        return 'ET_LINK_FAILURE_CHANGE: ' + OFGMessage.__str__(self) + str(self.link) + ' => ' + what + 'fail'
ET_MESSAGES.append(ETLinkFailureChange)

class ETComputationDone(OFGMessage):
    @staticmethod
    def get_type():
        return 0xFF

    def __init__(self, num_unplaced_flows, xid=0):
        OFGMessage.__init__(self, xid)
        self.num_unplaced_flows = int(num_unplaced_flows)

    def length(self):
        return OFGMessage.SIZE + 4

    def pack(self):
        body = struct.pack('> I', self.num_unplaced_flows)
        return OFGMessage.pack(self) + body

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        num_unplaced_flows = struct.unpack('> I', body[:4])[0]
        return ETPowerUsage(num_unplaced_flows, xid)

    def __str__(self):
        fmt = 'ET_COMPUTATION_DONE: ' + OFGMessage.__str__(self) + " %u flows could not be placed"
        return fmt % self.num_unplaced_flows
ET_MESSAGES.append(ETComputationDone)

ET_PROTOCOL = LTProtocol(OFG_MESSAGES + ET_MESSAGES, 'H', 'B')

def run_et_server(port, recv_callback):
    """Starts a server which listens for Elastic Tree clients on the specified port.

    @param port  the port to listen on
    @param recv_callback  the function to call with received message content
                         (takes two arguments: transport, msg)

    @return returns the new LTTwistedServer
    """
    from ltprotocol.ltprotocol import LTTwistedServer
    server = LTTwistedServer(ET_PROTOCOL, recv_callback)
    server.listen(port)
    reactor.run()