-- FwType 00000003
-- FwVersion 00aa55aa
-- HwType 00000013
-- HwVersion 00000001

--STORAGE
STO99999 = OBJECT:new(44, "STO99999")


-- setVar type support
_type = {}
CLU1760455328421 = OBJECT:new(0, 0xA833F8A, "CLU1760455328421")
-- NAME_CLU CLU1760455328421=CLU1760455328421



-- MODULES

-- IO_MODULES


function toboolean(value)
    local valueType = type(value)
    if valueType == "boolean" then
      return value
    elseif valueType == "number" then
      return value ~= 0
    elseif valueType == "string" then
      local numeric = tonumber(value)
      if numeric ~= nil then
         return numeric ~= 0
      elseif value == "false" then
         return false
      end
      return value and true
    elseif valueType == "nil" then
      return false
    end
    return value and true
end

function setVar(name, value)
    t = _type[name]
    if(t == "string") then
        _G[name] = tostring(value)
    elseif(t == "number") then
        _G[name] = tonumber(value)
    elseif(t == "boolean") then
        _G[name] = toboolean(value)
    else
        _G[name] = value
    end
end

function getVar(name)
	return _G[name]
end



function OnInit()

-- INIT_CLU_OBJECTS
CLU1760455328421:set(14, 22)
CLU1760455328421:set(20, "ssl://user:pass@localhost:8883")
CLU1760455328421:set(21, false)
CLU1760455328421:set(23, false)
CLU1760455328421:set(24, "homeassistant")

end

CLU1760455328421:add_event(0, OnInit)
