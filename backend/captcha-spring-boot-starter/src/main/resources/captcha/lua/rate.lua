-- KEYS[1] = contador de solicitudes; ARGV[1] = ventana (s). Devuelve el contador actual.
local n = redis.call('INCR', KEYS[1])
if n == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return n
