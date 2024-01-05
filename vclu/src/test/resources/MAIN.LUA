
collectgarbage("collect")
require "user"

collectgarbage("collect")
require "om"
collectgarbage("collect")


function checkAlive()
	return "00000000"
end

SYSTEM.Init()


repeat
	SYSTEM.Loop()
until 1==2

