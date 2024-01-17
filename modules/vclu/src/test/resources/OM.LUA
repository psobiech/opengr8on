-- FwType 00000003
-- FwVersion 00aa55aa
-- HwType 00000013
-- HwVersion 00000001

CLU0 = OBJECT:new(0, 0x7F000001, "CLU0")
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

end

CLU0:add_event(0, OnInit)
