-- FwType 00000003
-- FwVersion 00aa55aa
-- HwType 00000013
-- HwVersion 00000001

--STORAGE
STO99999 = OBJECT:new(44, "STO99999")

CLU0 = OBJECT:new(0, 0x7F000001, "CLU0")
-- NAME_CLU CLU0=CLU0

CLU1 = OBJECT:new(1, 0x7F000001, "CLU1")

-- MODULES

-- IO_MODULES


function setVar(name, value)
	_G[name] = value
end

function getVar(name)
	return _G[name]
end


function OnInit()

-- INIT_CLU_OBJECTS
CLU0:set(14, 22)
CLU0:set(20, "tcp://z2m:moh4aT8Kah9ethoh9veu7Xub9gah5tai@192.168.31.31:1883")
CLU0:set(21, 0)

end

CLU0:add_event(0, OnInit)
