

function onMqtt()

--BEGIN_INIT
--END_INIT

	print(MQT8715:get(0), ": ", MQT8715:get(1))
	
	MQT8715:execute(10, "test", MQT8715:get(1))
	
	MQT8715:execute(2)

end






function onTmr()

--BEGIN_INIT
--END_INIT

	print(getVar("asdasd"))
	setVar("asdasd", CLU221007568:execute(0, "getVar(\"sss\")")+ 1)
	print(getVar("asdasd"))
	CLU221007568:execute(0, "setVar(\"sss\", getVar(\"sss\")+ 1)")
	print(getVar("asdasd"))

end






function init()

--BEGIN_INIT
--END_INIT

	TIM6550:execute(0)

end






function onHttp()

--BEGIN_INIT
--END_INIT

	logDebug(HTT5497:get(10)["firstName"])

end




