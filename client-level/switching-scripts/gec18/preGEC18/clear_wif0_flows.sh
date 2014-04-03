
echo "Deleting wifi0 flows..."
curl http://localhost:8080/wm/staticflowentrypusher/clear/5/json | python -mjson.tool
curl http://localhost:8080/wm/staticflowentrypusher/clear/2/json | python -mjson.tool
echo "New flow tables by switch..."
curl http://localhost:8080/wm/staticflowentrypusher/list/all/json | python -mjson.tool
exit 0
