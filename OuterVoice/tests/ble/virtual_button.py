"""Local QA peripheral: netsim radio + real ATT/CCCD, never a physical Zeekr device.

Install requirements into OuterVoice/build/ble-test-deps. Run with that directory on
PYTHONPATH and an isolated netsimd gRPC endpoint. Control API binds localhost only.
"""
import asyncio
import json
import logging
from urllib.parse import urlparse, parse_qs
from bumble.device import Device, DeviceConfiguration
from bumble.hci import Address
from bumble.gatt import Service, Characteristic, CharacteristicValue
from bumble.transport import open_transport

ADDRESS = 'F0:F1:F2:F3:F4:63'

async def main():
    async with await open_transport('android-netsim:127.0.0.1:8877,name=OuterVoice-v163-test') as transport:
        config = DeviceConfiguration(name='ZEEKR_button', address=Address(ADDRESS), classic_enabled=False)
        device = Device.from_config_with_hci(config, transport.source, transport.sink)
        events = {'connections': 0, 'subscriptions': 0, 'writes': 0, 'notifications': 0}
        connections = set()
        wanted = False
        last_gesture = 1
        notify = Characteristic('2A60', Characteristic.Properties.NOTIFY | Characteristic.Properties.READ, 'READABLE', b'')
        async def reply(value):
            if len(value) > 2:
                payload = b'zk_v_1_2_1' if value[2] == 1 else b'psn=OV_VIRTUAL_BUTTON'
                await device.notify_subscribers(notify, payload)
        def write(connection, value):
            events['writes'] += 1
            asyncio.create_task(reply(bytes(value)))
        command = Characteristic('2A70', Characteristic.Properties.WRITE, 'WRITEABLE', CharacteristicValue(write=write))
        battery = Characteristic('2A19', Characteristic.Properties.READ | Characteristic.Properties.NOTIFY, 'READABLE', bytes([50]))
        device.add_services([Service('1820', [notify, command]), Service('180F', [battery])])
        def subscribed(*args):
            events['subscriptions'] += 1
            print(json.dumps({'event': 'CCCD', 'count': events['subscriptions']}), flush=True)
        notify.on('subscription', subscribed)
        def connected(connection):
            connections.add(connection); events['connections'] += 1
            print(json.dumps({'event': 'connected', 'count': events['connections']}), flush=True)
            def disconnected(reason):
                connections.discard(connection)
                if wanted: asyncio.create_task(advertise(last_gesture))
            connection.on('disconnection', disconnected)
        device.on('connection', connected)
        await device.power_on()
        counter = 0
        async def advertise(gesture):
            nonlocal counter
            counter = (counter + 1) % 16
            name = b'ZEEKR_button'
            raw = bytes([2, 1, 6, 8, 2, 255, counter, gesture, 50, 1, 2, 1, len(name)+1, 9]) + name
            await device.stop_advertising()
            device.advertising_data = raw
            await device.start_advertising(advertising_data=raw, scan_response_data=b'', auto_restart=False,
                                           advertising_interval_min=100, advertising_interval_max=100)
            print(json.dumps({'event': 'advertising', 'raw': raw.hex()}), flush=True)
        async def handle(reader, writer):
            try:
                header = await reader.readuntil(b'\r\n\r\n')
                path = header.split(b' ')[1].decode(); url = urlparse(path); query = parse_qs(url.query)
                if url.path == '/start':
                    nonlocal wanted, last_gesture
                    wanted = True; last_gesture = int(query.get('gesture', ['1'])[0]); await advertise(last_gesture)
                elif url.path == '/notify':
                    await device.notify_subscribers(notify, query.get('value', ['1'])[0].encode())
                    events['notifications'] += 1
                elif url.path == '/stop':
                    wanted = False
                    await device.stop_advertising()
                    for connection in list(connections):
                        try: await connection.disconnect()
                        except Exception: pass
                body = json.dumps({**events, 'active_connections': len(connections), 'address': ADDRESS}).encode()
                writer.write(b'HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: '+str(len(body)).encode()+b'\r\nConnection: close\r\n\r\n'+body)
                await writer.drain()
            except Exception as error:
                print('CONTROL ERROR '+repr(error), flush=True)
            finally: writer.close()
        server = await asyncio.start_server(handle, '127.0.0.1', 8878)
        print(json.dumps({'ready': True, 'address': ADDRESS}), flush=True)
        async with server: await server.serve_forever()

if __name__ == '__main__':
    logging.basicConfig(level=logging.WARNING)
    asyncio.run(main())
