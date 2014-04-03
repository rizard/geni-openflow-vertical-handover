import os
import urllib, json
import pprint


def findport(iface):

    URL = "http://localhost:8080/wm/core/controller/switches/json"
    Response = urllib.urlopen(URL)
    jsonResponse = json.loads(Response.read())
    output = json.dumps(jsonResponse)
   #print output.find(iface)
   #print output
    cnt=0
    while (output[output.find(iface)+len(iface)+73+cnt] != ','):cnt=cnt+1
   # print cnt
    return output[(output.find(iface)+len(iface)+73):(output.find(iface)+len(iface)+73+cnt)]
if __name__ == '__main__':
     aa=findport('eth3')
     print aa
    ##print findport('tap-eth')
