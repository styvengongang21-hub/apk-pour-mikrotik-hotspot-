# SALAM-WIFI expiration
:local sn ("exp-" . $user)
:if ([:len [/system scheduler find name=$sn]] = 0) do={
  :local p [/ip hotspot user get [find name=$user] profile]
  :local d [/ip hotspot user profile get [find name=$p] session-timeout]
  :if ([:typeof $d] = "time" && $d > 0s) do={
    /system scheduler add name=$sn disabled=yes comment=[:tostr ([:timestamp] + $d)]
  }
}
