function collectgarbage()
  -- NOP
end

function logDebug(...)
  print(...)

  api.logDebug(...)
end

function log(...)
  print(...)

  api.logInfo(...)
end

function logInfo(...)
  print(...)

  api.logInfo(...)
end

function logWarning(...)
  print(...)

  api.logWarning(...)
end

function logError(...)
  print(...)

  api.logError(...)
end

--
--
--

SYSTEM = {}
SYSTEM.__index = SYSTEM

function SYSTEM:Init()
  api.setup()
end

function SYSTEM:Loop()
  api.loop()
end

function SYSTEM:Wait(milliseconds)
  api.sleep(milliseconds)
end

function SYSTEM:clientRegister(remoteAddress, a, b, c, d)
  return api.clientRegister(remoteAddress, a, b, c, d)
end

function SYSTEM:clientDestroy(a, b, c)
  return api.clientDestroy(a, b, c)
end

function SYSTEM:fetchValues(d)
  return api.fetchValues(d)
end

function SYSTEM:mqttRegister(...)
  print(...)

  api.logInfo(...)
end

function SYSTEM:mqttDestroy(...)
  print(...)

  api.logInfo(...)
end

--
--
--

OBJECT = {}
OBJECT.__index = OBJECT

function OBJECT:new(idx, a, b, c)
  local self = {}
  setmetatable(self, OBJECT)

  if idx == 0 or idx == 1 then
    self['name'] = b

    self['_id'] = api.newObject(idx, b, a)
  elseif idx == 44 then
    self['name'] = a
    self['_id'] = api.newObject(idx, a, 0)
  else
    self['parent'] = a
    self['name'] = c

    self['_id'] = api.newObject(idx, c, 0)
  end

  return self
end

function OBJECT:get(idx)
  return api.get(self['_id'], idx)
end

function OBJECT:set(idx, value)
  api.set(self['_id'], idx, value)
end

function OBJECT:execute(idx, name)
  return api.execute(self['_id'], idx, name)
end

function OBJECT:add_event(idx, fn)
  api.addEvent(self['_id'], idx, fn)
end

--
--
--

GATE = {}
GATE.__index = GATE

function GATE:new(idx, a)
  local self = {}
  setmetatable(self, GATE)

  if idx == 121 then
    self['name'] = a

    self['_id'] = api.newGate(idx, a)
  else
    self['name'] = a

    self['_id'] = api.newGate(idx, a)
  end

  return self
end

function GATE:get(idx)
  return api.get(self['_id'], idx)
end

function GATE:set(idx, value)
  api.set(self['_id'], idx, value)
end

function GATE:execute(idx, name)
  return api.execute(self['_id'], idx, name)
end

function GATE:add_event(idx, fn)
  api.addEvent(self['_id'], idx, fn)
end
