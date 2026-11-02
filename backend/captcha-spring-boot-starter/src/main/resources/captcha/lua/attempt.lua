-- KEYS[1] = contador de fallos, KEYS[2] = clave de bloqueo
-- ARGV[1] = ventana (s), ARGV[2] = max-attempts, ARGV[3] = duración del bloqueo (s)
-- Devuelve los fallos acumulados, o 0 si se acaba de bloquear (el contador se reinicia).
local n = redis.call('INCR', KEYS[1])
if n == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if n >= tonumber(ARGV[2]) then
  redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
  redis.call('DEL', KEYS[1])
  return 0
end
return n
