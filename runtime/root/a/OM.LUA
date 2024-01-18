-- FwType 00000003
-- FwVersion 00aa55aa
-- HwType 00000013
-- HwVersion 00000001

--STORAGE
STO99999 = OBJECT:new(44, "STO99999")

CLU1704034933774 = OBJECT:new(0, 0xC0A81F27, "CLU1704034933774")
-- NAME_CLU CLU0=CLU1704034933774

CLU221007568 = OBJECT:new(1, 0xC0A81F1F, "CLU221007568")

MQT8715 = OBJECT:new(999, "MQT8715")
-- NAME_PERIPHERY mqtt=MQT8715
TIM6550 = OBJECT:new(6, "TIM6550")
-- NAME_PERIPHERY tmr=TIM6550
HTT5497 = GATE:new(121, "HTT5497")
-- NAME_PERIPHERY http=HTT5497

-- MODULES

-- IO_MODULES

asdasd = 333 -- number

function setVar(name, value)
	_G[name] = value
end

function getVar(name)
	return _G[name]
end

function EventsFor_MQT8715_0()
	MQT8715:execute(0, "zigbee2mqtt/#")
end
MQT8715:add_event(0, EventsFor_MQT8715_0)

function EventsFor_MQT8715_1()
	onMqtt(nil)
end
MQT8715:add_event(1, EventsFor_MQT8715_1)

function EventsFor_TIM6550_0()
	onTmr(nil)
end
TIM6550:add_event(0, EventsFor_TIM6550_0)

function EventsFor_HTT5497_1()
	onHttp(nil)
end
HTT5497:add_event(1, EventsFor_HTT5497_1)



function OnInit()

-- INIT_CLU_OBJECTS
CLU1704034933774:set(14, 22)
CLU1704034933774:set(20, "tcp://z2m:moh4aT8Kah9ethoh9veu7Xub9gah5tai@192.168.31.31:1883")
CLU1704034933774:set(21, 1)
MQT8715:set(0, "\z")
MQT8715:set(1, "\z")
TIM6550:set(0, 10000)
TIM6550:set(1, 1)
HTT5497:set(0, "https://mocktarget.apigee.net")
HTT5497:set(1, "/xml")
HTT5497:set(2, "\z")
HTT5497:set(3, "GET")
HTT5497:set(4, 5)
HTT5497:set(5, 0)
HTT5497:set(6, 2)
HTT5497:set(7, "\z")
HTT5497:set(8, "\z")
HTT5497:set(9, "\z")
HTT5497:set(10, "\z")

	init(nil) -- CluOnInit
end

CLU1704034933774:add_event(0, OnInit)
STO99999:execute(2, "asdasd")
