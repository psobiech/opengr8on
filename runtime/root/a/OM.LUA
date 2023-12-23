-- FwType 00000001
-- FwVersion 00000001
-- HwType aa55aa55
-- HwVersion 00000001

CLU0 = OBJECT:new(0, 0xC0A81F27, "CLU0")
-- NAME_CLU CLU0=CLU0



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

end

CLU0:add_event(0, OnInit)
