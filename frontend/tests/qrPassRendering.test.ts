import assert from 'node:assert/strict';
import React from 'react';
import ReactDOMServer from 'react-dom/server';
import { QRCodeSVG } from 'qrcode.react';
import type { UpcomingBooking } from '../src/types/booking.ts';

console.log('--- Running QR Pass Check-in Rendering Tests ---');

// 1. Verify QRCodeSVG generates authentic SVG with paths for a given token
{
  const token = 'QR_9f8d7c6b5a4e3d2c1b0a';
  const el = React.createElement(QRCodeSVG, {
    value: token,
    size: 88,
    level: 'M',
    marginSize: 1,
    fgColor: '#0f172a',
    bgColor: '#ffffff',
    title: `Check-in QR Pass for Hamsa Hrudai`,
  });
  const html = ReactDOMServer.renderToStaticMarkup(el);

  assert.ok(html.startsWith('<svg'), 'QR Code must be an SVG element');
  assert.ok(html.includes('viewBox="0 0'), 'QR Code SVG must include standard viewBox coordinates');
  assert.ok(html.includes('<path'), 'QR Code SVG must render standard vector paths representing QR matrix modules');
  assert.ok(html.includes('title>Check-in QR Pass for Hamsa Hrudai</title>'), 'Must render accessibility title');
  assert.ok(!html.includes(`[QR TOKEN: ${token}]`), 'Must not render placeholder bracketed text');
  console.log('✓ QRCodeSVG correctly produces vector SVG with valid 2D matrix modules');
}

// 2. Verify distinct tokens generate distinct QR matrix paths
{
  const hostToken = 'QR_HOST_HAMSA_TOKEN_123';
  const teammateToken = 'QR_TEAMMATE_BOMBA_TOKEN_456';

  const hostEl = React.createElement(QRCodeSVG, { value: hostToken, size: 88, level: 'M' });
  const teammateEl = React.createElement(QRCodeSVG, { value: teammateToken, size: 88, level: 'M' });

  const hostHtml = ReactDOMServer.renderToStaticMarkup(hostEl);
  const teammateHtml = ReactDOMServer.renderToStaticMarkup(teammateEl);

  assert.notEqual(hostHtml, teammateHtml, 'Different player tokens must produce different QR codes');
  assert.ok(hostHtml.includes('<path'), 'Host QR code must contain path data');
  assert.ok(teammateHtml.includes('<path'), 'Teammate QR code must contain path data');
  console.log('✓ Different player tokens generate distinct optical QR patterns');
}

// 3. Verify pass mapping integrity for a multi-player booking
{
  const testBooking: UpcomingBooking = {
    id: 'UB-101',
    slot: {
      id: 'm-1',
      session: 'morning',
      startTime: '06:00 AM',
      endTime: '06:30 AM',
      label: '06:00 AM – 06:30 AM',
    },
    courtId: 2,
    courtName: 'Court 2',
    bookingUser: 'Hamsa Hrudai',
    creditsDeducted: 1,
    status: 'Confirmed',
    players: [
      {
        studentId: '1',
        name: 'Hamsa Hrudai',
        admissionNumber: '23MIC7320',
        spotNumber: 1,
        qrToken: 'QR_HOST_HAMSA_TOKEN_123',
        checkedIn: false,
      },
      {
        studentId: '2',
        name: 'Bomba',
        admissionNumber: '23MIC7333',
        spotNumber: 2,
        qrToken: 'QR_TEAMMATE_BOMBA_TOKEN_456',
        checkedIn: false,
      },
    ],
  };

  // Ensure each player maintains their unique spot and token association
  const spot1Player = testBooking.players.find((p) => p.spotNumber === 1)!;
  const spot2Player = testBooking.players.find((p) => p.spotNumber === 2)!;

  assert.equal(spot1Player.name, 'Hamsa Hrudai');
  assert.equal(spot1Player.qrToken, 'QR_HOST_HAMSA_TOKEN_123');
  assert.equal(spot2Player.name, 'Bomba');
  assert.equal(spot2Player.qrToken, 'QR_TEAMMATE_BOMBA_TOKEN_456');

  // Verify rendering each pass independently
  const passes = testBooking.players.map((player) => {
    const qrSvg = ReactDOMServer.renderToStaticMarkup(
      React.createElement(QRCodeSVG, {
        value: player.qrToken,
        size: 88,
        level: 'M',
        marginSize: 1,
        fgColor: '#0f172a',
        bgColor: '#ffffff',
        title: `Check-in QR Pass for ${player.name}`,
      })
    );
    return {
      studentId: player.studentId,
      name: player.name,
      spotNumber: player.spotNumber,
      token: player.qrToken,
      qrSvg,
    };
  });

  assert.equal(passes.length, 2);
  assert.ok(passes[0].qrSvg.includes('<svg'));
  assert.ok(passes[1].qrSvg.includes('<svg'));
  assert.notEqual(passes[0].qrSvg, passes[1].qrSvg);
  console.log('✓ Each player pass strictly preserves individual identity, spot number, and unique QR SVG');
}

console.log('\nAll QR Pass Check-in Rendering Tests PASSED successfully!');
