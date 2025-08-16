-- FwType 00000003
-- FwVersion 00000200
-- HwType 00000013
-- HwVersion 00000001

--STORAGE
STO99999 = OBJECT:new(44, "STO99999")

CLU22XXXXXXX = OBJECT:new(0, 0xA489002, "CLU22XXXXXXX")
-- NAME_CLU CLU22XXXXXXX=CLU22XXXXXXX

CLU52XXXXXXX = OBJECT:new(1, 0xA489004, "CLU52XXXXXXX")
CLU50XXXXXXX = OBJECT:new(1, 0xA489008, "CLU50XXXXXXX")


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
    CLU22XXXXXXX:set(18, 0)
    CLU22XXXXXXX:set(20, 50)
    CLU22XXXXXXX:set(21, 230)
    CLU22XXXXXXX:set(22, "tempus1.gum.gov.pl")
    CLU22XXXXXXX:set(23, 0)
    CLU22XXXXXXX:set(25, "8.8.8.8")
    CLU22XXXXXXX:set(26, "8.8.4.4")
    CLU22XXXXXXX:set(28, 0.5)
    CLU22XXXXXXX:set(29, 25)
    CLU22XXXXXXX:set(30, 23)
    CLU22XXXXXXX:set(31, 0)

end

CLU22XXXXXXX:add_event(0, OnInit)
