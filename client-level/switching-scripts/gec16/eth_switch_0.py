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

flow1 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"src-dst-ether-tap",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"65534",
    "actions":"output=10"
    }
    
flow2 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"dst-src-ether-tap",
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
    'switch':"00:00:00:00:00:00:00:01",
    "name":"src-dst-ether-eth",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"16",
    "actions":"set-src-ip=130.127.39.221,set-src-mac=00:0d:b9:22:56:14,output=1"
    }
    
flow6 = {
    'switch':"00:00:00:00:00:00:00:01",
    "name":"dst-src-ether-eth",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"1",
    "actions":"set-dst-mac=12:51:16:90:8f:ee,output=16"
    }

flow7 = {
    'switch':"00:00:00:00:00:00:00:01",
    "name":"src-dst-arp-eth",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"16",
    "actions":"set-src-ip=130.127.39.221,set-src-mac=00:0d:b9:22:56:14,output=1"
    }
    
flow8 = {
    'switch':"00:00:00:00:00:00:00:01",
    "name":"dst-src-arp-eth",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"1",
    "actions":"set-dst-mac=12:51:16:90:8f:ee,output=16"
    }        
    
pusher.set(flow1)
pusher.set(flow2)
pusher.set(flow3)
pusher.set(flow4)
pusher.set(flow5)
pusher.set(flow6)
pusher.set(flow7)
pusher.set(flow8)
