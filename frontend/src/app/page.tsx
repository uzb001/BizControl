import Link from 'next/link';

const features = [
  { icon: '📦', title: 'Products & Stock', desc: 'Manage products, track inventory, get low-stock alerts automatically.' },
  { icon: '🛒', title: 'Sales Management', desc: 'Create sales, track payments, manage customer debts with full history.' },
  { icon: '🏭', title: 'Purchases', desc: 'Record purchases, track supplier debts, update stock automatically.' },
  { icon: '👥', title: 'Customers & Suppliers', desc: 'Full profiles, debt tracking, payment history and statements.' },
  { icon: '💰', title: 'Cashbox & Bank', desc: 'Track every cash and bank movement with categories and filters.' },
  { icon: '📊', title: 'Reports & Export', desc: 'Sales, profit, stock reports with date filters and Excel export.' },
  { icon: '🔍', title: 'Advanced Filters', desc: 'Every table has detailed search, filter, sort and export options.' },
  { icon: '📋', title: 'Audit Logs', desc: 'Every action is logged. Full audit trail for owner transparency.' },
];

const steps = [
  { num: '1', title: 'Create Account', desc: 'Sign up with your email or phone, enter company details.' },
  { num: '2', title: 'Add Products', desc: 'Add your products with prices, categories, stock levels.' },
  { num: '3', title: 'Record Operations', desc: 'Create sales and purchases — stock updates automatically.' },
  { num: '4', title: 'Track & Analyze', desc: 'See your profit, debts, cash balance and reports in real time.' },
];

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white">
      {/* Navbar */}
      <nav className="sticky top-0 z-50 bg-white border-b border-gray-200">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-sm">B</span>
            </div>
            <span className="font-bold text-gray-900 text-lg">BizControl.uz</span>
          </div>
          <div className="flex items-center gap-3">
            <Link href="/login" className="btn-secondary btn-sm">Login</Link>
            <Link href="/signup" className="btn-primary btn-sm">Get Started Free</Link>
          </div>
        </div>
      </nav>

      {/* Hero */}
      <section className="bg-gradient-to-br from-blue-600 to-blue-800 text-white py-24 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <div className="inline-flex items-center gap-2 bg-blue-500/30 px-4 py-1.5 rounded-full text-sm mb-6">
            <span className="w-2 h-2 bg-green-400 rounded-full animate-pulse"></span>
            Business Management Platform for SMEs
          </div>
          <h1 className="text-5xl font-bold leading-tight mb-6">
            Manage Your Business<br />Like a Pro
          </h1>
          <p className="text-xl text-blue-100 mb-10 max-w-2xl mx-auto">
            Products, warehouse, sales, purchases, cash, customer debts, supplier debts and reports — all in one place. Easier than 1C, more powerful than Excel.
          </p>
          <div className="flex items-center justify-center gap-4">
            <Link href="/signup" className="btn bg-white text-blue-700 hover:bg-blue-50 btn-lg font-semibold">
              Start Free Today
            </Link>
            <Link href="/login" className="btn border border-white/40 text-white hover:bg-white/10 btn-lg">
              Sign In
            </Link>
          </div>
        </div>
      </section>

      {/* Problem section */}
      <section className="py-20 px-6 bg-gray-50">
        <div className="max-w-5xl mx-auto">
          <h2 className="text-3xl font-bold text-center text-gray-900 mb-4">Sound familiar?</h2>
          <p className="text-center text-gray-500 mb-12">The struggle is real for every small business</p>
          <div className="grid md:grid-cols-2 gap-6">
            <div className="card p-6 border-l-4 border-l-orange-400">
              <h3 className="font-semibold text-gray-800 mb-2">📊 Excel is a mess</h3>
              <ul className="text-gray-600 text-sm space-y-2">
                <li>• Stock doesn't update when you make a sale</li>
                <li>• Debts and payments tracked manually with mistakes</li>
                <li>• No real-time profit calculation</li>
                <li>• Files get corrupted, formulas break</li>
              </ul>
            </div>
            <div className="card p-6 border-l-4 border-l-red-400">
              <h3 className="font-semibold text-gray-800 mb-2">🏢 1C is too complicated</h3>
              <ul className="text-gray-600 text-sm space-y-2">
                <li>• Requires expensive implementation and training</li>
                <li>• Complex interface not designed for trading</li>
                <li>• Hard to customize for your specific needs</li>
                <li>• Slow and heavy for day-to-day operations</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-20 px-6">
        <div className="max-w-6xl mx-auto">
          <h2 className="text-3xl font-bold text-center text-gray-900 mb-4">Everything You Need</h2>
          <p className="text-center text-gray-500 mb-12">One platform for your complete business operations</p>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {features.map((f) => (
              <div key={f.title} className="card p-5 hover:shadow-md transition-shadow">
                <div className="text-3xl mb-3">{f.icon}</div>
                <h3 className="font-semibold text-gray-900 mb-2">{f.title}</h3>
                <p className="text-gray-500 text-sm">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="py-20 px-6 bg-gray-50">
        <div className="max-w-4xl mx-auto">
          <h2 className="text-3xl font-bold text-center text-gray-900 mb-4">How It Works</h2>
          <p className="text-center text-gray-500 mb-12">Get started in minutes, not weeks</p>
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {steps.map((s) => (
              <div key={s.num} className="text-center">
                <div className="w-12 h-12 bg-blue-600 text-white rounded-full flex items-center justify-center text-lg font-bold mx-auto mb-4">
                  {s.num}
                </div>
                <h3 className="font-semibold text-gray-900 mb-2">{s.title}</h3>
                <p className="text-gray-500 text-sm">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Comparison */}
      <section className="py-20 px-6">
        <div className="max-w-4xl mx-auto">
          <h2 className="text-3xl font-bold text-center text-gray-900 mb-12">BizControl vs. the Rest</h2>
          <div className="card overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-blue-600 text-white">
                  <th className="px-5 py-3 text-left">Feature</th>
                  <th className="px-5 py-3 text-center">Excel</th>
                  <th className="px-5 py-3 text-center">1C</th>
                  <th className="px-5 py-3 text-center font-bold">BizControl</th>
                </tr>
              </thead>
              <tbody>
                {[
                  ['Auto stock updates', '❌', '✅', '✅'],
                  ['Real-time profit', '❌', '✅', '✅'],
                  ['Easy to use', '⚠️', '❌', '✅'],
                  ['Advanced filters', '⚠️', '⚠️', '✅'],
                  ['Excel export', '✅', '⚠️', '✅'],
                  ['Low cost', '✅', '❌', '✅'],
                  ['Web-based, no install', '❌', '❌', '✅'],
                  ['Alert system', '❌', '⚠️', '✅'],
                ].map(([feat, excel, one_c, biz]) => (
                  <tr key={feat} className="border-t border-gray-100">
                    <td className="px-5 py-3 font-medium text-gray-700">{feat}</td>
                    <td className="px-5 py-3 text-center">{excel}</td>
                    <td className="px-5 py-3 text-center">{one_c}</td>
                    <td className="px-5 py-3 text-center font-bold text-blue-600">{biz}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 px-6 bg-blue-600 text-white text-center">
        <div className="max-w-2xl mx-auto">
          <h2 className="text-3xl font-bold mb-4">Start Managing Your Business Today</h2>
          <p className="text-blue-100 mb-8">Join hundreds of businesses that use BizControl.uz for their daily operations.</p>
          <Link href="/signup" className="btn bg-white text-blue-700 hover:bg-blue-50 btn-lg font-semibold">
            Create Free Account
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-gray-900 text-gray-400 py-10 px-6 text-center">
        <div className="flex items-center justify-center gap-2 mb-4">
          <div className="w-6 h-6 bg-blue-600 rounded flex items-center justify-center">
            <span className="text-white font-bold text-xs">B</span>
          </div>
          <span className="font-bold text-white">BizControl.uz</span>
        </div>
        <p className="text-sm">Business management platform for small and medium companies.</p>
        <p className="text-xs mt-4">© 2025 BizControl.uz. All rights reserved.</p>
      </footer>
    </div>
  );
}
