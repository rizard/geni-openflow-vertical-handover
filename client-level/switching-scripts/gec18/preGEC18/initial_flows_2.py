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
    "name":"src-tap-dst-wlan0-ether",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"1",
    "actions":"output=7"
    }
    
flow2 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"dst-tap-src-wlan0-ether",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"7",
    "actions":"output=1"
    }

flow3 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"src-tap-dst-wlan0-arp",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"1",
    "actions":"output=7"
    }
    
flow4 = {
    'switch':"00:00:00:00:00:00:00:05",
    "name":"dst-tap-src-wlan0-arp",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"7",
    "actions":"output=1"
    }
    
flow5 = {
    'switch':"00:00:00:00:00:00:00:02",
    "name":"src-wlan0-dst-ntwk-ether",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"12",
    "actions":"output=1"
    }
    
flow6 = {
    'switch':"00:00:00:00:00:00:00:02",
    "name":"dst-wlan0-src-ntwk-ether",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x800",
    "ingress-port":"1",
    "actions":"output=12"
    }

flow7 = {
    'switch':"00:00:00:00:00:00:00:02",
    "name":"src-wlan0-dst-ntwk-arp",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"12",
    "actions":"output=1"
    }
    
flow8 = {
    'switch':"00:00:00:00:00:00:00:02",
    "name":"dst-wlan0-src-ntwk-arp",
    "priority":"32768",
    "active":"true",
    "ether-type":"0x806",
    "ingress-port":"1",
    "actions":"output=12"
    }        
    
pusher.set(flow1)
pusher.set(flow2)
pusher.set(flow3)
pusher.set(flow4)
pusher.set(flow5)
pusher.set(flow6)
pusher.set(flow7)
pusher.set(flow8)
