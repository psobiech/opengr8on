

function onMqtt()

--BEGIN_INIT
--END_INIT

	CLU0:execute(0, MQT8715:get(0).. ": " .. MQT8715:get(1))
	
	MQT8715:execute(10, "test", MQT8715:get(1))
	
	MQT8715:execute(2)

end






function onTmr()

--BEGIN_INIT
--END_INIT

	TIM6550:execute(0)

end






function init()

--BEGIN_INIT
--END_INIT

	onTmr(nil)
	
	HTT5497:execute(0)

end




