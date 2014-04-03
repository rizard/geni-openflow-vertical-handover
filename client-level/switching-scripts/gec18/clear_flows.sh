
echo "********Deleting flows********"
curl http://localhost:8080/wm/staticflowentrypusher/clear/all/json | python -mjson.tool
echo "********New flow tables by switch********"
curl http://localhost:8080/wm/staticflowentrypusher/list/all/json | python -mjson.tool
exit 0
