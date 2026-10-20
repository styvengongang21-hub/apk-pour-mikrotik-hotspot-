:local now [:timestamp]
:foreach s in=[/system scheduler find where name~"^exp-"] do={
  :local c [/system scheduler get $s comment]
  :if ([:len $c] > 0) do={
    :if ($now >= [:totime $c]) do={
      :local n [/system scheduler get $s name]
      :local u [:pick $n 4 [:len $n]]
      :do { /ip hotspot user set [find name=$u] disabled=yes } on-error={}
      :do { /ip hotspot active remove [find user=$u] } on-error={}
      /system scheduler remove $s
    }
  }
}
