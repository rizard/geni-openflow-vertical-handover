import httplib
import json
class StaticFlowPusher(object):
    def __init__(self, server):
        self.server = server

    def get(self, data):
        ret = self.rest_call({}, 'GET')
        return json.loads(ret[2])

    def set(self, data):
        ret = self.rest_call(data, 'POST')
        return ret[0] == 200

    def remove(self, objtype, data):
        ret = self.rest_call(data, 'DELETE')
        return ret[0] == 200

    def rest_call(self, data, action):
        path = '/wm/staticflowentrypusher/json'
        headers = {
            'Content-type': 'application/json',
            'Accept': 'application/json',
            }
        body = json.dumps(data)
        conn = httplib.HTTPConnection(self.server, 8080)
        conn.request(action, path, body, headers)
        response = conn.getresponse()
        ret = (response.status, response.reason, response.read())
        print ret
        conn.close()
        return ret

pusher = StaticFlowPusher('127.0.0.1')

# Rewrite the src-mac as WIMAX for IPv4 packets so the device
# we're communicating with will know how to get back to us.
flow1 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"src-dst-ip-tap",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"65534",
    "actions":"output=10"
    }
    
flow2 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"dst-src-ip-tap",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"10",
    "actions":"output=65534"
    }

flow3 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"src-dst-arp-tap",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"65534",
    "actions":"output=10"
    }

# We want to send all ARP packets to the controller for rewrite
# since OF can't rewrite ARP packets. Thus, we do not push this flow.
flow4 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"dst-src-arp-tap",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"10",
    "actions":"output=65534"
    }
    
flow5 = {
    'switch':"00:00:00:00:00:00:00:04",
    "name":"src-dst-ip-wimax",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"13",
    "actions":"set-src-mac=00:1d:e1:3b:48:1d,output=1"
    }

# Rewrite the dst-mac as TAP for ingress IPv4 packets    
flow6 = {
    'switch':"00:00:00:00:00:00:00:04",
    "name":"dst-src-ip-wimax",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"1",
    "actions":"set-dst-mac=12:51:16:90:8f:ee,output=13"
    }

# We want to send all ARP packets to the controller for rewrite
# since OF can't rewrite ARP packets. Thus, we do not push this flow.
flow7 = {
    'switch':"00:00:00:00:00:00:00:04",
    "name":"src-dst-arp-wimax",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"13",
    "actions":"output=1"
    }
    
flow8 = {
    'switch':"00:00:00:00:00:00:00:04",
    "name":"dst-src-arp-wimax",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"1",
    "actions":"output=13"
    }        
    
pusher.set(flow1)
pusher.set(flow2)
pusher.set(flow3)
#pusher.set(flow4)
pusher.set(flow5)
pusher.set(flow6)
#pusher.set(flow7)
pusher.set(flow8)
